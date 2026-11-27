# RetrySight Cloud — capabilities

**Installation tier:** `TIER_4_CLOUD`  
**Spring profile:** `tier-cloud`

## Adds on top of Integration

| Area | Capabilities |
|------|--------------|
| **Push ingest** | Agents/services push events (not only pull batches) |
| **Object archive** | S3-compatible cold storage for retention datasets |
| **Archive restore** | Query and rehydrate cold archive objects |
| **Git enterprise** | Project-root redaction and enterprise privacy controls |

## Databases & storage

| Store | Role |
|-------|------|
| PostgreSQL (`agent_retry_cloud`) | System of record |
| ClickHouse (`agent_retry_cloud`) | Analytics warehouse |
| Object storage (MinIO dev / S3 prod) | Long-term archive blobs |

## Default in baseline monorepo

The root `application.yml` defaults to `TIER_4_CLOUD`. This folder provides an **isolated, production-oriented compose stack** with distinct image tags, ports, and object storage wiring.
