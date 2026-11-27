#!/usr/bin/env bash
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

# API smoke pack (S1–S12) against a running manager.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"

require_cmd curl
require_cmd python3

echo "Smoke validation against ${MANAGER_URL}"
echo

# S1 Login
TOKEN="$(login_token || true)"
if [[ -n "${TOKEN}" ]]; then
  pass "S1 login JWT issued"
else
  fail "S1 login JWT issued"
  summary
fi

# S2 platformFeatures
PF="$(graphql "$TOKEN" 'query { platformFeatures { installationTier costEnabled reportsEnabled mcpEnabled gitContextCaptureEnabled gitContextQueryEnabled pushIngestEnabled } }')"
if graphql_has_errors "$PF"; then
  fail "S2 platformFeatures"
else
  pass "S2 platformFeatures"
  echo "     $(echo "$PF" | python3 -c 'import json,sys; d=json.loads(sys.stdin.read().strip().splitlines()[0]); print(d["data"]["platformFeatures"])')"
fi

# S3 dashboardSummary
DS="$(graphql "$TOKEN" 'query { dashboardSummary { totalTasks avgRetriesPerTask retryTokenCostUsd } }')"
if graphql_has_errors "$DS"; then
  fail "S3 dashboardSummary"
else
  pass "S3 dashboardSummary"
fi

# S5 tasks
TASKS="$(graphql "$TOKEN" 'query { tasks(page: 0, size: 3) { totalCount items { id description sourceTool projectContext { repoName gitBranch } } } }')"
if graphql_has_errors "$TASKS"; then
  fail "S5 tasks list"
else
  pass "S5 tasks list"
  TASK_ID="$(echo "$TASKS" | python3 -c 'import json,sys; d=json.loads(sys.stdin.read().strip().splitlines()[0]); items=d["data"]["tasks"]["items"]; print(items[0]["id"] if items else "")')"
  if [[ -n "$TASK_ID" ]]; then
    TD="$(graphql "$TOKEN" 'query($id: ID!) { task(id: $id) { id sourceTool projectContext { repoName gitCommit } } }' "{\"id\":\"${TASK_ID}\"}")"
    if graphql_has_errors "$TD"; then
      fail "S5/S10 task detail + projectContext"
    else
      pass "S5/S10 task detail + projectContext field"
    fi
  else
    skip "S5/S10 task detail (no tasks in DB)"
  fi
fi

# S6 agents
AG="$(graphql "$TOKEN" 'query { agents { id hostname lastHeartbeatAt } }')"
if graphql_has_errors "$AG"; then
  fail "S6 agents"
else
  pass "S6 agents"
fi

# S7 costSummary
FROM="$(python3 -c 'from datetime import datetime,timedelta,timezone; print((datetime.now(timezone.utc)-timedelta(days=7)).strftime("%Y-%m-%dT00:00:00Z"))')"
TO="$(python3 -c 'from datetime import datetime,timezone; print(datetime.now(timezone.utc).strftime("%Y-%m-%dT23:59:59Z"))')"
CS="$(graphql "$TOKEN" \
  'query($from: DateTime, $to: DateTime) { costSummary(from: $from, to: $to) { totalCostUsd retryCostUsd costByTool { tool value } } }' \
  "{\"from\":\"${FROM}\",\"to\":\"${TO}\"}")"
if graphql_has_errors "$CS"; then
  fail "S7 costSummary (check :toGROUP / INTERNAL_ERROR regressions)"
  echo "     $(echo "$CS" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("errors"))' 2>/dev/null || true)"
else
  pass "S7 costSummary"
fi

# S8 retryAnalysis
RA="$(graphql "$TOKEN" \
  'query($from: DateTime, $to: DateTime) { retryAnalysis(from: $from, to: $to) { unattributedRetryCount retriesByModel { model value } } }' \
  "{\"from\":\"${FROM}\",\"to\":\"${TO}\"}")"
if graphql_has_errors "$RA"; then
  fail "S8 retryAnalysis"
else
  pass "S8 retryAnalysis"
fi

# S9 reports
REPORT_CODE="$(http_code GET "${MANAGER_URL}/api/v1/reports/options" -H "Authorization: Bearer ${TOKEN}")"
if [[ "$REPORT_CODE" == "200" ]]; then
  pass "S9 reports options (${REPORT_CODE})"
elif [[ "$REPORT_CODE" == "404" ]]; then
  pass "S9 reports gated off (${REPORT_CODE})"
else
  fail "S9 reports options (HTTP ${REPORT_CODE})"
fi

# S11 MCP token
MCP_CODE="$(http_code POST "${MANAGER_URL}/api/v1/auth/mcp-token" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"${APM_EMAIL}\",\"password\":\"${APM_PASSWORD}\"}")"
if [[ "$MCP_CODE" == "200" ]]; then
  pass "S11 MCP token (${MCP_CODE})"
elif [[ "$MCP_CODE" == "404" ]]; then
  pass "S11 MCP gated off (${MCP_CODE})"
else
  fail "S11 MCP token (HTTP ${MCP_CODE})"
fi

# S12 ingest auth
HB_BODY="$(python3 - <<'PY'
import json
from datetime import datetime, timezone
print(json.dumps({
  "hostname": "smoke-host",
  "developerEmail": "smoke@example.com",
  "activeCollectors": ["CURSOR"],
  "queueDepth": 0,
  "reportedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
}))
PY
)"
AUTH_CODE="$(curl -s -o /dev/null -w '%{http_code}' -X POST "${MANAGER_URL}/api/v1/ingest/heartbeat" \
  -H "Content-Type: application/json" \
  -H "X-Agent-Api-Key: ${AGENT_API_KEY}" \
  -d "$HB_BODY")"
NOAUTH_CODE="$(curl -s -o /dev/null -w '%{http_code}' -X POST "${MANAGER_URL}/api/v1/ingest/heartbeat" \
  -H "Content-Type: application/json" \
  -d "$HB_BODY")"
if [[ "$NOAUTH_CODE" == "401" || "$NOAUTH_CODE" == "403" || "$NOAUTH_CODE" == "302" ]]; then
  pass "S12 ingest rejects missing API key (${NOAUTH_CODE})"
else
  fail "S12 ingest should reject missing key (got ${NOAUTH_CODE})"
fi
if [[ "$AUTH_CODE" =~ ^2 ]]; then
  pass "S12 ingest accepts agent API key (${AUTH_CODE})"
elif [[ "$AUTH_CODE" == "400" ]]; then
  pass "S12 ingest auth OK (400 body validation — key accepted)"
else
  fail "S12 ingest with API key (HTTP ${AUTH_CODE})"
fi

# Optional UI reachability
UI_CODE="$(http_code GET "${UI_URL}/" || echo "000")"
if [[ "$UI_CODE" == "200" ]]; then
  pass "UI reachable at ${UI_URL}"
else
  skip "UI not reachable at ${UI_URL} (HTTP ${UI_CODE}) — start Vite: cd ui/manager-dashboard && npm run dev"
fi

skip "S4 Live subscriptions — manual UI check"
summary
