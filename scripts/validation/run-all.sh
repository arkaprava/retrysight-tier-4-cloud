#!/usr/bin/env bash
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

# Full validation pack: unit tests + optional live smoke/tier/capacity.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

RUN_SMOKE="${RUN_SMOKE:-auto}"
RUN_TIER="${RUN_TIER:-0}"
RUN_CAPACITY="${RUN_CAPACITY:-0}"

echo "== Unit / module tests =="
(cd "$ROOT" && mvn -q -pl shared/agent-contract,manager/manager-app,agent/agent-app -am test)
echo "Maven tests OK"

if [[ -d "$ROOT/mcp/node_modules" ]] || [[ "${RUN_MCP_TEST:-1}" == "1" ]]; then
  echo "== MCP tests =="
  (cd "$ROOT/mcp" && npm test)
fi

echo "== UI build =="
(cd "$ROOT/ui/manager-dashboard" && npm run build)

MANAGER_URL="${MANAGER_URL:-http://localhost:28477}"
if [[ "$RUN_SMOKE" == "1" ]] || { [[ "$RUN_SMOKE" == "auto" ]] && curl -sf -o /dev/null "${MANAGER_URL}/graphiql" 2>/dev/null; }; then
  echo "== Live smoke (${MANAGER_URL}) =="
  "${SCRIPT_DIR}/smoke.sh"
elif [[ "$RUN_SMOKE" == "auto" ]]; then
  echo "SKIP live smoke — manager not reachable at ${MANAGER_URL}"
  echo "     Start manager then: RUN_SMOKE=1 ./scripts/validation/run-all.sh"
fi

if [[ "$RUN_TIER" == "1" ]]; then
  echo "== Tier matrix =="
  "${SCRIPT_DIR}/tier-matrix.sh"
fi

if [[ "$RUN_CAPACITY" == "1" ]]; then
  echo "== Capacity probe =="
  "${SCRIPT_DIR}/capacity-probe.sh"
fi

echo
echo "Validation pack complete."
echo "Docs: docs/VALIDATION.md docs/SECURITY.md docs/CAPACITY.md"
