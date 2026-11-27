# Infrastructure (Docker)

Use the **repository root** — [`docker-compose.yml`](../docker-compose.yml), [`docs/DEPLOYMENT.md`](../docs/DEPLOYMENT.md), [`docs/RELEASE.md`](../docs/RELEASE.md).

## Images (tier-cloud)

| Service | Default tag |
|---------|-------------|
| postgres | `retrysight/tier-cloud-postgres:local` |
| manager | `retrysight/tier-cloud-manager:local` |
| frontend | `retrysight/tier-cloud-frontend:local` |
| agent | `retrysight/tier-cloud-agent:local` |

Also runs **ClickHouse** and **MinIO** (dev S3 archive) from upstream images.

Default ports: dashboard **8086**, manager **28477**, Postgres **5435**, ClickHouse **8125**, MinIO console **9002**.

Prefer root [`scripts/`](../scripts/) over legacy `infra/scripts/`.
