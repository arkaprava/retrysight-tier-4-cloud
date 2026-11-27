# Capacity Analysis

## Growth model

| Driver | Rate driver | Impact |
|--------|-------------|--------|
| Agents | Developer machines | Low |
| Tasks | ~1 per IDE session | Medium |
| **task_events** | Edits / tools / session events | **Highest** |
| **token_usage** | Per LLM turn | High |
| Dashboard polls | UI 15–30s | Read load |

## Indexes already present

- `token_usage(recorded_at)`, `token_usage(task_id)`  
- `tasks(agent_id)`, `tasks(started_at)`, git columns (`repo_name`, `git_branch`, `remote_host`)  
- `task_events(task_id)`, `task_events(occurred_at)`  

Enterprise BYO RDBMS (Oracle / SQL Server / MySQL / Postgres): see [BYO_RDBMS.md](BYO_RDBMS.md).

## Probe script

With manager running and a valid agent API key:

```bash
export MANAGER_URL=http://localhost:28477
export AGENT_API_KEY=dev-agent-key
./scripts/validation/capacity-probe.sh
```

The probe:

1. Records baseline GraphQL `dashboardSummary` / `costSummary` latency  
2. Sends N synthetic heartbeat + empty-ish batches (configurable)  
3. Re-measures query latency  
4. Prints a summary table to stdout and `docs/capacity-results/` (gitignored samples OK)

### Variables

| Variable | Default | Meaning |
|----------|---------|---------|
| `PROBE_BATCHES` | `20` | Number of ingest batches |
| `PROBE_EVENTS_PER_BATCH` | `50` | Items per batch (synthetic) |
| `PROBE_CONCURRENCY` | `1` | Parallel ingest workers |

## Measurement checklist

| Test | Method | Record |
|------|--------|--------|
| Baseline idle | 1 agent | CPU, heap, DB size, p95 GraphQL |
| Ingest ramp | 1× → 10× probe rate | accept/skip/error, p95 |
| Query load | Concurrent cost + dashboard | p50/p95/p99 |
| Soak | 4–8h at peak | Pool exhaustion, disk growth |

## Sizing bands (validate empirically)

| Band | Fleet | Guidance |
|------|-------|----------|
| Small | ≤25 agents | Single manager + Postgres; retention ≤90d |
| Medium | 25–100 | Monitor cost query times; plan retention |
| Large | 100+ | Event archival, partitioning, read scaling |

## Formulas (fill after probes)

```
events_per_day ≈ agents × sessions_per_agent_day × events_per_session
disk_gb_per_day ≈ events_per_day × avg_event_bytes / 1e9
+ token_usage_rows_per_day × avg_row_bytes / 1e9
```

## Recommended follow-ups after numbers

1. Retention job for `task_events` older than N days  
2. Time index on `task_events(occurred_at)` if range scans appear  
3. Connection pool sizing (`HikariCP`) under concurrent UI users  
4. Agent `max-batch-size` / interval tuning if manager lags  
5. Enable ClickHouse analytics sink for large fleets — see [CLICKHOUSE_ANALYTICS.md](CLICKHOUSE_ANALYTICS.md)
