#!/usr/bin/env bash
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

# Shared helpers for validation scripts.
set -euo pipefail

MANAGER_PORT="${MANAGER_PORT:-28477}"
MANAGER_URL="${MANAGER_URL:-http://localhost:${MANAGER_PORT}}"
# Prefer localhost (Chrome headless can truncate pages served via 127.0.0.1).
UI_URL="${UI_URL:-http://localhost:5173}"
APM_EMAIL="${APM_EMAIL:-secadmin@local}"
APM_PASSWORD="${APM_PASSWORD:-changeme}"
AGENT_API_KEY="${AGENT_API_KEY:-dev-agent-key}"

PASS=0
FAIL=0
SKIP=0

green() { printf '\033[32m%s\033[0m\n' "$*"; }
red() { printf '\033[31m%s\033[0m\n' "$*"; }
yellow() { printf '\033[33m%s\033[0m\n' "$*"; }

pass() { green "PASS  $*"; PASS=$((PASS + 1)); }
fail() { red "FAIL  $*"; FAIL=$((FAIL + 1)); }
skip() { yellow "SKIP  $*"; SKIP=$((SKIP + 1)); }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

http_code() {
  local method="$1" url="$2"
  shift 2
  curl -s -o /dev/null -w '%{http_code}' -X "$method" "$url" "$@"
}

json_post() {
  local url="$1" body="$2"
  shift 2
  curl -s -X POST "$url" \
    -H 'Content-Type: application/json' \
    "$@" \
    -d "$body"
}

login_token() {
  local resp
  resp="$(json_post "${MANAGER_URL}/api/v1/auth/login" \
    "{\"email\":\"${APM_EMAIL}\",\"password\":\"${APM_PASSWORD}\"}")"
  python3 -c 'import json,sys; print(json.load(sys.stdin).get("token",""))' <<<"$resp"
}

graphql() {
  local token="$1" query="$2" variables="${3:-{}}"
  local body q_b64 v_b64
  q_b64="$(printf '%s' "$query" | base64 | tr -d '\n')"
  v_b64="$(printf '%s' "$variables" | base64 | tr -d '\n')"
  body="$(
    Q_B64="$q_b64" V_B64="$v_b64" python3 - <<'PY'
import base64, json, os
query = base64.b64decode(os.environ["Q_B64"]).decode().strip()
raw_vars = base64.b64decode(os.environ["V_B64"]).decode().strip() or "{}"
variables = json.JSONDecoder().raw_decode(raw_vars)[0]
print(json.dumps({"query": query, "variables": variables}))
PY
  )"
  json_post "${MANAGER_URL}/graphql" "$body" -H "Authorization: Bearer ${token}"
}

graphql_has_errors() {
  printf '%s' "$1" | python3 -c 'import json,sys
raw=sys.stdin.read().strip()
if not raw:
    raise SystemExit(1)
d=json.JSONDecoder().raw_decode(raw)[0]
raise SystemExit(0 if d.get("errors") else 1)'
}

summary() {
  echo
  echo "Results: pass=${PASS} fail=${FAIL} skip=${SKIP}"
  if [[ "$FAIL" -gt 0 ]]; then
    exit 1
  fi
}
