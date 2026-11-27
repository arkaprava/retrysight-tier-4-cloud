#!/usr/bin/env bash
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

# Lightweight capacity probe: GraphQL latency + synthetic ingest ramp.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"

require_cmd curl
require_cmd python3

PROBE_BATCHES="${PROBE_BATCHES:-20}"
PROBE_EVENTS_PER_BATCH="${PROBE_EVENTS_PER_BATCH:-50}"
OUT_DIR="${ROOT}/docs/capacity-results"
mkdir -p "$OUT_DIR"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_FILE="${OUT_DIR}/probe-${STAMP}.txt"

echo "Capacity probe → ${MANAGER_URL}"
echo "batches=${PROBE_BATCHES} events_per_batch=${PROBE_EVENTS_PER_BATCH}"
echo

TOKEN="$(login_token)"
if [[ -z "$TOKEN" ]]; then
  echo "Cannot login — is the manager up?" >&2
  exit 1
fi

measure_graphql() {
  local label="$1" query="$2"
  python3 - <<PY
import json, time, urllib.request
url = "${MANAGER_URL}/graphql"
token = """${TOKEN}"""
body = json.dumps({"query": """${query}"""}).encode()
req = urllib.request.Request(url, data=body, headers={
    "Content-Type": "application/json",
    "Authorization": f"Bearer {token}",
}, method="POST")
times = []
errors = 0
for _ in range(5):
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = json.load(resp)
            if data.get("errors"):
                errors += 1
    except Exception:
        errors += 1
    times.append((time.perf_counter() - t0) * 1000)
times.sort()
p50 = times[len(times)//2]
p95 = times[max(0, int(len(times)*0.95)-1)]
print(f"{label}: p50={p50:.1f}ms p95={p95:.1f}ms errors={errors}/5")
PY
}

{
  echo "Capacity probe ${STAMP}"
  echo "MANAGER_URL=${MANAGER_URL}"
  echo
  echo "== Baseline GraphQL =="
  measure_graphql dashboard 'query { dashboardSummary { totalTasks } }'
  measure_graphql cost 'query { costSummary { totalCostUsd } }'
  echo
  echo "== Ingest ramp =="
} | tee "$OUT_FILE"

python3 - <<PY | tee -a "$OUT_FILE"
import json, time, urllib.request, uuid
from datetime import datetime, timezone

manager = "${MANAGER_URL}"
key = "${AGENT_API_KEY}"
batches = int("${PROBE_BATCHES}")
events_per = int("${PROBE_EVENTS_PER_BATCH}")

def post(path, body):
    req = urllib.request.Request(
        manager + path,
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json", "X-Agent-Api-Key": key},
        method="POST",
    )
    t0 = time.perf_counter()
    with urllib.request.urlopen(req, timeout=60) as resp:
        data = json.load(resp)
    return (time.perf_counter() - t0) * 1000, data

now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
hb_ms, hb = post("/api/v1/ingest/heartbeat", {
    "hostname": "capacity-probe-host",
    "developerEmail": "capacity-probe@example.com",
    "activeCollectors": ["CURSOR"],
    "queueDepth": 0,
    "reportedAt": now,
})
print(f"heartbeat: {hb_ms:.1f}ms agentId={hb.get('agentId')}")

latencies = []
accepted = skipped = errors = 0
for i in range(batches):
    task_id = str(uuid.uuid4())
    items = []
    for j in range(events_per):
        items.append({
            "idempotencyKey": f"probe-{task_id}-{j}",
            "type": "TASK_EVENT",
            "payload": {
                "taskId": task_id,
                "eventType": "EDIT",
                "occurredAt": now,
                "retry": j % 3 == 0,
                "sequenceNum": j,
                "payload": {"probe": True, "i": i, "j": j},
            },
        })
    # Also start the task once per batch
    batch = {
        "heartbeat": {
            "hostname": "capacity-probe-host",
            "developerEmail": "capacity-probe@example.com",
            "activeCollectors": ["CURSOR"],
            "queueDepth": len(items),
            "reportedAt": now,
        },
        "taskStarts": [{
            "taskId": task_id,
            "externalSessionId": f"probe-session-{task_id}",
            "sourceTool": "CURSOR",
            "description": f"capacity probe batch {i}",
            "startedAt": now,
            "metadata": {"probe": True},
        }],
        "items": items,
    }
    try:
        ms, data = post("/api/v1/ingest/batch", batch)
        latencies.append(ms)
        accepted += int(data.get("accepted", 0) or 0)
        skipped += int(data.get("skipped", 0) or 0)
        err_list = data.get("errors") or []
        errors += len(err_list) if isinstance(err_list, list) else int(err_list or 0)
    except Exception as ex:
        errors += 1
        print(f"batch {i} failed: {ex}")

latencies.sort()
if latencies:
    p50 = latencies[len(latencies)//2]
    p95 = latencies[max(0, int(len(latencies)*0.95)-1)]
    total = sum(latencies)
    eps = (batches * events_per) / (total / 1000.0) if total else 0
    print(f"ingest batches={batches}: p50={p50:.1f}ms p95={p95:.1f}ms accepted={accepted} skipped={skipped} errors={errors}")
    print(f"approx event throughput: {eps:.1f} events/sec (wall clock of sequential posts)")
else:
    print("no successful batches")
PY

{
  echo
  echo "== Post-ingest GraphQL =="
  measure_graphql dashboard 'query { dashboardSummary { totalTasks } }'
  measure_graphql cost 'query { costSummary { totalCostUsd } }'
  echo
  echo "Results written to ${OUT_FILE}"
} | tee -a "$OUT_FILE"

pass "capacity probe completed — see ${OUT_FILE}"
summary
