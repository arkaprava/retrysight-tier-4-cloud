# Agent offline buffer policy

When the manager is unreachable, the agent stores pending events in a local SQLite buffer (`agent.upload.buffer-path`, default `~/.agent-retry/buffer.db`). Caps prevent unbounded disk growth during prolonged offline periods.

## Cap order

After every insert, the agent enforces caps in this order:

1. **Age** (`buffer-max-age-hours`, default `168` / 7 days) — delete rows older than the cutoff
2. **Row count** (`buffer-max-rows`, default `50_000`) — delete oldest rows by insert order until under the limit
3. **File size** (`buffer-max-bytes`, default `268435456` / 256 MB) — delete oldest rows in batches until the database file fits

Set any cap to `0` to disable it.

## Drop behavior

Row and byte caps use **oldest-first eviction by SQLite row id**, not task-aware grouping. Under sustained offline pressure the agent may drop:

- `TASK_START` events while later events for the same task remain
- Partial task histories that cannot be reconstructed on upload

Age cap deletes by timestamp regardless of task boundaries.

This is intentional for a lightweight local queue: the agent favors bounded disk use over perfect task integrity during long outages.

## Observability

- **Logs:** each cap enforcement logs a warning with the number of rows dropped
- **Heartbeat:** `bufferDroppedTotal` reports cumulative drops since agent start (when &gt; 0)
- **Heartbeat:** `bufferDroppedTotal`, discovery results, and parse errors are reported when non-zero
- **Dashboard:** queue depth on the **Agents** page; discovery status badges are implemented in tier-1-core manager UI (other tiers receive heartbeat data but may not display badges yet)

## Fleet tuning

For laptops that may be offline for days, consider:

```yaml
agent:
  upload:
    buffer-max-rows: 100000
    buffer-max-age-hours: 336   # 14 days
    buffer-max-bytes: 536870912 # 512 MB
```

For constrained devices, lower caps and accept more data loss during extended outages.

## Related settings

| Setting | Default | Purpose |
|---------|---------|---------|
| `upload.max-batch-size` | 100 | Events per upload request |
| `upload.interval-seconds` | 30 | Upload retry interval |
| `discovery.refresh-interval-seconds` | 300 | Discovery rescan interval |

See also `config/agent/fleet-config.example.yaml` and `agent/agent-app/config.example.yaml`.
