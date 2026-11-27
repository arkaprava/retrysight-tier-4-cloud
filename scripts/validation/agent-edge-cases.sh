#!/usr/bin/env bash
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

# Agent edge-case validation: unit tests + lightweight integration fixtures.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

# Per-tier, per-run temp dir so parallel invocations across repos do not clash.
TIER_SLUG="$(basename "${ROOT}")"
RUN_DIR="$(mktemp -d "${TMPDIR:-/tmp}/retrysight-agent-edge.${TIER_SLUG}.${$}.XXXXXX")"
cleanup() { rm -rf "${RUN_DIR}"; }
trap cleanup EXIT

FIXTURE_DIR="${RUN_DIR}/fixtures"
DISCOVER_JSON="${RUN_DIR}/discover.json"
DISCOVER_ERR="${RUN_DIR}/discover.err"
AGENT_LOG_1="${RUN_DIR}/agent-1.log"
AGENT_LOG_2="${RUN_DIR}/agent-2.log"
LOCK_HOME="${RUN_DIR}/home"

PASS=0
FAIL=0
SKIP=0

pass() { echo "PASS  $1"; PASS=$((PASS + 1)); }
fail() { echo "FAIL  $1"; FAIL=$((FAIL + 1)); }
skip() { echo "SKIP  $1"; SKIP=$((SKIP + 1)); }

echo "=== Agent edge-case validation (${TIER_SLUG}) ==="
echo "    temp dir: ${RUN_DIR}"
echo

echo "-- Maven unit tests (agent module) --"
if mvn -pl agent/agent-app test -Dtest=JsonlFileWatcherTest,UploadAckPlannerTest,IdleSessionTrackerTest,CollectorDiscoveryServiceTest,CursorCollectorTest,PlatformPathsTest -Dsurefire.failIfNoSpecifiedTests=false -q; then
  pass "A1 core edge-case unit tests"
else
  fail "A1 core edge-case unit tests"
fi

echo
echo "-- Fixture: JSONL rotation --"
mkdir -p "${FIXTURE_DIR}"
python3 - <<'PY' "${FIXTURE_DIR}"
import json, os, subprocess, sys, tempfile, textwrap
fixture = sys.argv[1]
log_dir = os.path.join(fixture, "cursor", "projects")
os.makedirs(log_dir, exist_ok=True)
log_file = os.path.join(log_dir, "session.jsonl")
with open(log_file, "w", encoding="utf-8") as handle:
    handle.write(json.dumps({"role": "user", "message": {"content": [{"type": "text", "text": "<user_query>hello</user_query>"}]}}) + "\n")
    handle.write(json.dumps({"type": "turn_ended", "status": "success"}) + "\n")
with open(log_file, "w", encoding="utf-8") as handle:
    handle.write(json.dumps({"role": "user", "message": {"content": [{"type": "text", "text": "<user_query>rotated</user_query>"}]}}) + "\n")
print(log_file)
PY
pass "A2 JSONL rotation fixture prepared"

echo
echo "-- Fixture: discovery recency --"
RECENT_DIR="${FIXTURE_DIR}/recent"
mkdir -p "${RECENT_DIR}"
echo '{"role":"user"}' > "${RECENT_DIR}/fresh.jsonl"
if [[ -n "$(find "${RECENT_DIR}" -name '*.jsonl' -mmin -60 | head -1)" ]]; then
  pass "A3 recency fixture has fresh log"
else
  fail "A3 recency fixture has fresh log"
fi

echo
echo "-- CLI discover (requires built agent jar) --"
JAR="$(ls "${ROOT}"/agent/agent-app/target/agent-app-*.jar 2>/dev/null | head -1 || true)"
if [[ -z "${JAR}" ]]; then
  skip "A4 discover CLI (agent jar not built — run ./scripts/build.sh)"
else
  AGENT_MANAGER_URL=http://127.0.0.1:9 AGENT_API_KEY=smoke-key \
    java -jar "${JAR}" \
      --agent.cli.command=discover \
      --agent.platform.auto-discovery=true \
      --spring.main.log-startup-info=false \
      --logging.level.root=WARN \
      >"${DISCOVER_JSON}" 2>"${DISCOVER_ERR}" || true
  if python3 "${ROOT}/scripts/validation/parse_discover_json.py" "${DISCOVER_JSON}" 2>/dev/null; then
    pass "A4 discover CLI emits JSON"
  else
    fail "A4 discover CLI emits JSON"
    sed -n '1,20p' "${DISCOVER_ERR}" || true
  fi
fi

echo
echo "-- Single-instance lock --"
if [[ -n "${JAR}" ]]; then
  export HOME="${LOCK_HOME}"
  mkdir -p "${HOME}/.agent-retry"
  java -jar "${JAR}" \
    --agent.identity.lock-file="${HOME}/.agent-retry/agent.lock" \
    --agent.identity.id-file="${HOME}/.agent-retry/agent-id" \
    --agent.upload.buffer-path="${HOME}/.agent-retry/buffer.db" \
    --agent.manager.url=http://127.0.0.1:9 \
    --spring.main.lazy-initialization=true >"${AGENT_LOG_1}" 2>&1 &
  PID1=$!
  sleep 3
  if java -jar "${JAR}" \
    --agent.identity.lock-file="${HOME}/.agent-retry/agent.lock" \
    --agent.identity.id-file="${HOME}/.agent-retry/agent-id" \
    --agent.upload.buffer-path="${HOME}/.agent-retry/buffer.db" \
    --agent.manager.url=http://127.0.0.1:9 \
    --spring.main.lazy-initialization=true >"${AGENT_LOG_2}" 2>&1; then
    fail "A5 single-instance lock blocks second process"
  else
    if grep -q "already running" "${AGENT_LOG_2}"; then
      pass "A5 single-instance lock blocks second process"
    else
      fail "A5 single-instance lock blocks second process"
    fi
  fi
  kill "${PID1}" 2>/dev/null || true
  wait "${PID1}" 2>/dev/null || true
else
  skip "A5 single-instance lock (agent jar not built)"
fi

echo
echo "=== Summary: ${PASS} passed, ${FAIL} failed, ${SKIP} skipped ==="
if [[ "${FAIL}" -gt 0 ]]; then
  exit 1
fi
