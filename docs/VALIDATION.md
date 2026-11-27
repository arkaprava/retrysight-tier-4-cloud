# RetrySight validation guide

Executable validation for RetrySight Cloud (`TIER_4_CLOUD`): smoke tests, agent hardening, push ingest, MinIO archive, and capacity probes.

## Quick start

```bash
./scripts/validation/smoke.sh
./scripts/validation/agent-edge-cases.sh
./scripts/validation/run-all.sh
```

| Variable | Default | Notes |
|----------|---------|-------|
| `MANAGER_URL` | `http://localhost:28477` | |
| `UI_URL` | `http://localhost:8086` | Docker dashboard |
| `APM_EMAIL` / `APM_PASSWORD` | `secadmin@local` / `changeme` | |
| `AGENT_API_KEY` | `dev-agent-key` | |

## Agent hardening

```bash
./scripts/validation/agent-edge-cases.sh
```

## CI

**Release** workflow on every PR — [RELEASE.md](./RELEASE.md) (Helm: ClickHouse + MinIO).

```bash
mvn -pl shared/agent-contract,manager/manager-app,agent/agent-app -am test
cd ui/manager-dashboard && npm run build
```

## Manual smoke

1. http://localhost:8086 — login  
2. Admin → Archive with MinIO (`http://minio:9000`, bucket `retrysight-archive`)  
3. Push ingest enabled — see [DEPLOYMENT.md](./DEPLOYMENT.md)  
4. `./scripts/validation/smoke.sh` with `MANAGER_URL=http://localhost:28477`

## Related docs

- [SECURITY.md](./SECURITY.md)
- [CAPACITY.md](./CAPACITY.md)
- [CLICKHOUSE_ANALYTICS.md](./CLICKHOUSE_ANALYTICS.md)
