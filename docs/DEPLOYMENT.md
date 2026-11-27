# RetrySight Cloud — deployment

## Local full stack

```bash
cp .env.example .env
./scripts/build.sh
./scripts/up.sh --profile agent
```

## Object archive (MinIO dev)

1. Open MinIO console: http://localhost:9002 (`minioadmin` / `minioadmin`)
2. Bucket `retrysight-archive` is created on first start via init script
3. In RetrySight Admin → Archive, set destination type **S3**:
   - Endpoint: `http://minio:9000` (from manager container) or `http://localhost:9000` (host)
   - Bucket: `retrysight-archive`
   - Access key / secret: see `.env.example`

For AWS S3, GCS, or Azure Blob, copy [`config/archive/object-storage.env.example`](../config/archive/object-storage.env.example) and configure via archive policy UI or env.

## Fleet agent & release

```bash
./scripts/retrysight-agent install
./scripts/package-agent.sh          # native installers (auto-start hooks included)
./scripts/validation/agent-edge-cases.sh
```

| Package | Auto-start after reboot / login |
|---------|----------------------------------|
| macOS `.pkg` | LaunchAgent `com.retrysight.agent` (`RunAtLoad` + `KeepAlive`) |
| Linux `.deb` | User systemd unit `retrysight-agent.service` (`systemctl --global enable`) |
| Windows `.msi` | Ships `register-autostart.ps1` (run once, or use install-agent-service.ps1) → Scheduled task `RetrySightAgent` (`AtLogOn`) |

Manual / jar installs: `./infra/scripts/install-agent-service.sh` or `infra/windows/install-agent-service.ps1`.

See [RELEASE.md](./RELEASE.md) for GitHub Actions artifacts and Helm (ClickHouse + MinIO enabled).

## Push ingest

Enabled by `tier-cloud` profile (`phase4-push-ingest: true`). Agents and OTel collectors can push to manager ingest endpoints with valid API keys.

## Ports (defaults)

| Service | Port |
|---------|------|
| Frontend | 8086 |
| Manager | 28477 |
| PostgreSQL | 5435 |
| ClickHouse HTTP | 8125 |
| MinIO API | 9000 |
| MinIO console | 9002 |

## Production

- Replace MinIO with managed object storage
- `SPRING_PROFILES_ACTIVE=docker,tier-cloud,prod`
- Enable `SECURITY_ALLOW_DEFAULT_SECRETS=false`
- Use IAM roles instead of static keys where possible
