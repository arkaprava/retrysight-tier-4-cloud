#!/usr/bin/env bash
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

# Start ephemeral managers per tier profile and assert feature gates.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"

require_cmd curl
require_cmd python3
require_cmd java

JAR="${ROOT}/manager/manager-app/target/manager-app-1.0.0.jar"
if [[ ! -f "$JAR" ]]; then
  echo "Building manager JAR..."
  (cd "$ROOT" && mvn -q -pl manager/manager-app -am package -DskipTests)
fi

pick_port() {
  python3 - <<'PY'
import socket
s = socket.socket()
s.bind(('127.0.0.1', 0))
print(s.getsockname()[1])
s.close()
PY
}

assert_features() {
  local label="$1" port="$2" expect_cost="$3" expect_mcp="$4" expect_git_query="$5"
  local expect_contract_review="$6" expect_contract_apply="$7"
  local url="http://127.0.0.1:${port}"
  local token resp cost mcp gitq review apply
  token="$(MANAGER_URL="$url" login_token)"
  [[ -n "$token" ]] || { fail "${label}: login"; return; }
  resp="$(MANAGER_URL="$url" graphql "$token" \
    'query { platformFeatures { costEnabled mcpEnabled gitContextQueryEnabled pushIngestEnabled contractCostReviewEnabled contractCostApplyEnabled installationTier } }')"
  if MANAGER_URL="$url" graphql_has_errors "$resp"; then
    fail "${label}: platformFeatures errors"
    return
  fi
  cost="$(echo "$resp" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["platformFeatures"]["costEnabled"])')"
  mcp="$(echo "$resp" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["platformFeatures"]["mcpEnabled"])')"
  gitq="$(echo "$resp" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["platformFeatures"]["gitContextQueryEnabled"])')"
  review="$(echo "$resp" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["platformFeatures"]["contractCostReviewEnabled"])')"
  apply="$(echo "$resp" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["platformFeatures"]["contractCostApplyEnabled"])')"
  # normalize Python True/False
  cost="$(echo "$cost" | tr '[:upper:]' '[:lower:]')"
  mcp="$(echo "$mcp" | tr '[:upper:]' '[:lower:]')"
  gitq="$(echo "$gitq" | tr '[:upper:]' '[:lower:]')"
  review="$(echo "$review" | tr '[:upper:]' '[:lower:]')"
  apply="$(echo "$apply" | tr '[:upper:]' '[:lower:]')"

  local ok=1
  [[ "$cost" == "$expect_cost" ]] || { fail "${label}: costEnabled want=${expect_cost} got=${cost}"; ok=0; }
  [[ "$mcp" == "$expect_mcp" ]] || { fail "${label}: mcpEnabled want=${expect_mcp} got=${mcp}"; ok=0; }
  [[ "$gitq" == "$expect_git_query" ]] || { fail "${label}: gitContextQueryEnabled want=${expect_git_query} got=${gitq}"; ok=0; }
  [[ "$review" == "$expect_contract_review" ]] || { fail "${label}: contractCostReviewEnabled want=${expect_contract_review} got=${review}"; ok=0; }
  [[ "$apply" == "$expect_contract_apply" ]] || { fail "${label}: contractCostApplyEnabled want=${expect_contract_apply} got=${apply}"; ok=0; }

  local mcp_http
  mcp_http="$(curl -s -o /dev/null -w '%{http_code}' -X POST "${url}/api/v1/auth/mcp-token" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"${APM_EMAIL}\",\"password\":\"${APM_PASSWORD}\"}")"
  if [[ "$expect_mcp" == "true" && "$mcp_http" != "200" ]]; then
    fail "${label}: MCP token HTTP want=200 got=${mcp_http}"
    ok=0
  fi
  if [[ "$expect_mcp" == "false" && "$mcp_http" != "404" ]]; then
    fail "${label}: MCP token HTTP want=404 got=${mcp_http}"
    ok=0
  fi

  if [[ "$ok" -eq 1 ]]; then
    pass "${label}: cost=${cost} mcp=${mcp} gitQuery=${gitq} contractReview=${review} contractApply=${apply} mcpHttp=${mcp_http}"
  fi
}

run_tier() {
  local profile="$1" expect_cost="$2" expect_mcp="$3" expect_git_query="$4"
  local expect_contract_review="$5" expect_contract_apply="$6"
  local port pid log
  port="$(pick_port)"
  log="/tmp/apm-tier-${profile}-${port}.log"
  echo "Starting profile=${profile} on port ${port}..."
  java -jar "$JAR" \
    --spring.profiles.active="dev-h2,${profile}" \
    --server.port="${port}" \
    >"$log" 2>&1 &
  pid=$!

  local ready=0 i
  for i in $(seq 1 60); do
    if curl -sf "http://127.0.0.1:${port}/graphiql" >/dev/null 2>&1 \
      || curl -sf -o /dev/null -w '' "http://127.0.0.1:${port}/api/v1/auth/login" >/dev/null 2>&1; then
      # wait until login works
      if MANAGER_URL="http://127.0.0.1:${port}" login_token >/dev/null 2>&1; then
        ready=1
        break
      fi
    fi
    if ! kill -0 "$pid" 2>/dev/null; then
      fail "${profile}: manager exited early — see ${log}"
      return
    fi
    sleep 1
  done

  if [[ "$ready" -ne 1 ]]; then
    fail "${profile}: manager did not become ready — see ${log}"
    kill "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
    return
  fi

  assert_features "$profile" "$port" "$expect_cost" "$expect_mcp" "$expect_git_query" \
    "$expect_contract_review" "$expect_contract_apply"
  kill "$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
}

echo "Tier matrix validation"
echo

# cost mcp gitQuery contractReview contractApply
run_tier tier-core false false false false false
run_tier tier-analytics true false false true false
run_tier tier-integration true true true true true
run_tier tier-cloud true true true true true

summary
