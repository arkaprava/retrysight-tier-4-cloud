# RetrySight Cloud

[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/arkaprava/retrysight-tier-4-cloud?sort=semver)](https://github.com/arkaprava/retrysight-tier-4-cloud/releases)

**See every retry. Know every dollar.** RetrySight is self-hosted observability for AI coding agents
(Cursor, Claude Code, Codex and more): tasks, retries, tokens and cost across your whole fleet.

Full enterprise tier: Integration **plus** push ingest, object storage archive, cold restore, and git enterprise privacy controls.

Installation tier `TIER_4_CLOUD` · Spring profile `docker,tier-cloud` · [retrysight.com](https://retrysight.com)

## The RetrySight lineup

Each tier is a standalone, self-contained repository. Higher tiers include everything below them.

| Tier | Repository | Adds |
|------|------------|------|
| Core | [`retrysight-tier-1-core`](https://github.com/arkaprava/retrysight-tier-1-core) | Ingest, dashboard, tasks, live feed, collector registry, retention |
| Analytics | [`retrysight-tier-2-analytics`](https://github.com/arkaprava/retrysight-tier-2-analytics) | + cost analytics, reports, evaluations, extended collectors, git context |
| Integration | [`retrysight-tier-3-integration`](https://github.com/arkaprava/retrysight-tier-3-integration) | + MCP server, SSO tools API, ClickHouse analytics, traces, patterns |
| **Cloud** | [`retrysight-tier-4-cloud`](https://github.com/arkaprava/retrysight-tier-4-cloud) | + push ingest, S3/MinIO archive, cold restore, git enterprise privacy |

Looking for a single-user desktop console? See [RetrySight Lite](https://github.com/arkaprava/retrysight-lite).

## Install

### Docker Compose (prebuilt images)

```bash
curl -fsSL https://app.retrysight.com/enterprise/tier-cloud/latest/docker-compose.yml -o docker-compose.yml
docker compose up -d
```

Dashboard: http://localhost:8086 · Manager API: http://localhost:28477

Multi-arch images (amd64, arm64) are published to GHCR (`docker pull ghcr.io/arkaprava/retrysight-tier-4-cloud/manager:1.0.0`), and as
`docker save` tarballs on the [releases page](https://github.com/arkaprava/retrysight-tier-4-cloud/releases) and at
`https://app.retrysight.com/enterprise/tier-cloud/latest/` for air-gapped installs (`docker load -i retrysight-manager-1.0.0-linux-amd64.tar.gz`; arm64 tarballs too).

### Kubernetes (Helm)

```bash
curl -fsSLO https://app.retrysight.com/enterprise/tier-cloud/latest/retrysight-1.0.0.tgz
helm upgrade --install retrysight ./retrysight-1.0.0.tgz -n retrysight --create-namespace
```

The chart generates the database password, JWT secret, admin password and agent API key on first
install and keeps them across upgrades. `helm status retrysight -n retrysight` prints how to read them.
See [`docs/RELEASE.md`](docs/RELEASE.md) for values and ingress options.

### Collector agent

Native installers (Windows `.msi`, macOS `.pkg`, Linux `.deb`) and the agent jar are attached to each
[release](https://github.com/arkaprava/retrysight-tier-4-cloud/releases).

## Build from source

### Components

| Component | Image | Storage |
|-----------|-------|---------|
| PostgreSQL 16 | `retrysight/tier-cloud-postgres:local` | OLTP |
| ClickHouse 24 | `clickhouse/clickhouse-server:24.8-alpine` | Analytics |
| MinIO | `cgr.dev/chainguard/minio:latest-dev` | S3-compatible archive (dev) |
| Manager / Frontend / Agent | `retrysight/tier-cloud-*:local` | — |

### Quick start

From the **repository root**:

```bash
cp .env.example .env
./scripts/build.sh
./scripts/up-with-agent.sh
```

| Service | URL |
|---------|-----|
| Dashboard | http://localhost:8086 |
| Manager API | http://localhost:28477 |
| PostgreSQL | localhost:5435 |
| ClickHouse | http://localhost:8125 |
| MinIO console | http://localhost:9002 (`minioadmin` / `minioadmin`) |

Configure object archive in Admin → Archive using MinIO endpoint `http://minio:9000`, bucket `retrysight-archive`.

### Docs

- [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) — production notes
- [`docs/RELEASE.md`](docs/RELEASE.md) — CI, Helm (ClickHouse + MinIO), native agents

## Repository layout

| Path | Purpose |
|------|---------|
| `manager/manager-app` | Spring Boot manager (GraphQL + ingest REST) |
| `agent/agent-app` | Collector agent (Windows, macOS, Linux) |
| `ui/manager-dashboard` | React dashboard |
| `shared/agent-contract` | Shared ingest DTOs |
| `mcp/` | MCP server (host process) |
| `helm/retrysight/` | Kubernetes Helm chart |
| `infra/`, `docker/` | Dockerfiles, PostgreSQL image and init scripts |
| `config/agent/` | Collector + fleet config examples |
| `scripts/` | Build, run, packaging and validation helpers |
| `docs/` | Documentation |

## Contributing

Issues and pull requests are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md).
Please report security issues privately as described in [SECURITY.md](SECURITY.md).

## License

Licensed under the [Apache License, Version 2.0](LICENSE). Copyright © 2026 Arka Prava Ghosh.
