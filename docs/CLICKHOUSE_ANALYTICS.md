# ClickHouse analytics sink

Optional **analytics warehouse** for large fleets. The RDBMS remains the system of record; ClickHouse receives an async dual-write of `token_usage` and `task_events` and can serve cost aggregate queries.

## Architecture

```
Agent → Manager ingest → RDBMS (commit)
                      └afterCommit→ buffer → ClickHouse (JSONEachRow)
UI costSummary ← prefer ClickHouse aggregates (fallback RDBMS)
```

| Concern | Store |
|---------|--------|
| Agents, users, SSO, task headers, idempotency | BYO RDBMS |
| Token / event facts for cost trends | ClickHouse (when enabled) |
| Retry-cost attribution / top tasks | Still RDBMS today |

## Enable (Docker)

```bash
cd infra
# Start ClickHouse + set manager env
export CLICKHOUSE_ENABLED=true
export CLICKHOUSE_URL=http://clickhouse:8123
docker compose --profile analytics up -d clickhouse
docker compose up -d --build manager
```

Local manager against Compose ClickHouse:

```bash
export CLICKHOUSE_ENABLED=true
export CLICKHOUSE_URL=http://localhost:8123
export CLICKHOUSE_DATABASE=agent_retry
```

## Configuration

| Property / env | Default | Meaning |
|----------------|---------|---------|
| `manager.analytics.clickhouse.enabled` / `CLICKHOUSE_ENABLED` | `false` | Dual-write + schema init |
| `manager.analytics.clickhouse.read-enabled` / `CLICKHOUSE_READ_ENABLED` | `true` | Prefer CH for cost aggregates |
| `CLICKHOUSE_URL` | `http://localhost:8123` | HTTP interface |
| `CLICKHOUSE_USER` / `CLICKHOUSE_PASSWORD` | `default` / empty | Basic auth |
| `CLICKHOUSE_DATABASE` | `agent_retry` | Database name |
| `CLICKHOUSE_FLUSH_INTERVAL_MS` | `1000` | Buffer flush period |
| `CLICKHOUSE_FLUSH_BATCH_SIZE` | `500` | Max rows per insert |

Feature gate: `CLICKHOUSE_ANALYTICS` (Tier 3+). GraphQL exposes `platformFeatures.clickhouseAnalyticsEnabled`.

## Tables (auto-created)

- `token_usage_analytics` — `ReplacingMergeTree`, partitioned by month  
- `task_events_analytics` — same  

Idempotency keys are part of the sort key so replays collapse under `ReplacingMergeTree`.

## Enterprise / BYO warehouse

Point `CLICKHOUSE_URL` at your managed ClickHouse (or compatible HTTP endpoint). No JDBC driver is required — the manager uses the HTTP interface.

For Snowflake/BigQuery instead of ClickHouse, keep the same dual-write pattern behind `AnalyticsEventSink` and add a new sink implementation.

## Failure behavior

- RDBMS ingest never blocks on ClickHouse.  
- Failed flushes re-queue the batch and log a warning.  
- Cost reads fall back to the RDBMS if ClickHouse errors or returns empty Optional.  

## Related

- [BYO_RDBMS.md](BYO_RDBMS.md) — OLTP database portability  
- [CAPACITY.md](CAPACITY.md) — fleet sizing  
