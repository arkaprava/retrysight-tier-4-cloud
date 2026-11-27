# Claude Cowork OpenTelemetry ingest

Admin runbook for sending Claude Cowork telemetry into RetrySight.

Cowork is a distinct product from Claude Code. Do **not** fold sessions into `CLAUDE_CODE`. Ingested sessions appear as `sourceTool=CLAUDE_COWORK`.

## Endpoint

| Item | Value |
|------|--------|
| URL | `http(s)://<manager-host>:28477/api/v1/otel/v1/logs` |
| Method | `POST` |
| Content-Type | `application/json` (OTLP/HTTP JSON) |
| Auth | `X-Agent-Api-Key: <agent-api-key>` or `Authorization: Bearer <agent-api-key>` |

Convenience aliases:

- `POST /api/v1/otel/ingest` — treats payloads with `resourceLogs` or `events` as logs
- Flat test shape: `{ "events": [ { "event.name": "user_prompt", "service.name": "cowork", ... } ] }`

Metrics (`/api/v1/otel/v1/metrics`) are accepted but not persisted yet.

## Cowork Admin configuration

1. In Claude Cowork / Anthropic org monitoring, enable OpenTelemetry export.
2. Set OTLP HTTP endpoint to the manager logs URL above (or to an OpenTelemetry Collector that forwards there).
3. Configure the agent API key as a header on the exporter / collector.
4. Confirm `service.name` is `cowork` (or contains `cowork`).

If Desktop cannot reach the manager directly:

```
Claude Desktop (Cowork) → OTel Collector → manager /api/v1/otel/v1/logs
```

## Event mapping

| Cowork event | Platform item | Notes |
|--------------|---------------|--------|
| (first event per `session.id`) | `TASK_START` | Deterministic task id from `cowork-session:<session.id>` |
| `user_prompt` | `TASK_EVENT` / `SESSION_START` | Prompt body only if `manager.privacy.store-full-prompts=true` |
| `api_request` | `TOKEN_USAGE` | Tokens + `cost_usd` + model |
| `tool_result` | `TASK_EVENT` | Edit / fail / reject heuristics |
| `tool_decision` | `TASK_EVENT` | Accept / reject |
| `api_error` | `TASK_EVENT` (retry) | Status + attempt |
| `assistant_response` | Optional event | Only when full-prompt storage is on |

Correlation keys: `session.id`, `prompt.id`, `event.sequence`.  
Workspace: first entry of `workspace.host_paths` → `metadata.projectRoot`.

## Config flags

| Property / env | Default | Purpose |
|----------------|---------|---------|
| `manager.otel.ingest-enabled` / `OTEL_INGEST_ENABLED` | `true` | Master switch for OTel routes |
| `manager.otel.cowork-mapping-enabled` / `OTEL_COWORK_MAPPING_ENABLED` | `true` | Map Cowork logs into ingest tables |
| `manager.privacy.store-full-prompts` | `false` | Store prompt/response bodies |
| `manager.cowork.analytics-api-*` | off | Phase-3 Analytics API reconcile stub |

## Privacy

Default: **no** prompt or assistant response bodies. Cost, retries, and tool timelines do not require content capture. Enable full storage only with explicit compliance review.

## Smoke test

```bash
curl -sS -X POST "http://localhost:28477/api/v1/otel/v1/logs" \
  -H "Content-Type: application/json" \
  -H "X-Agent-Api-Key: ${AGENT_API_KEY:-dev-agent-key}" \
  -d '{
    "events": [
      {
        "event.name": "user_prompt",
        "service.name": "cowork",
        "session.id": "smoke-sess-1",
        "prompt.id": "p1",
        "user.email": "dev@example.com",
        "event.sequence": 1,
        "workspace.host_paths": ["/tmp/demo"]
      },
      {
        "event.name": "api_request",
        "service.name": "cowork",
        "session.id": "smoke-sess-1",
        "prompt.id": "p1",
        "event.sequence": 2,
        "model": "claude-sonnet",
        "input_tokens": 10,
        "output_tokens": 5,
        "cost_usd": 0.001
      }
    ]
  }'
```

Expect JSON with `"mappedItems": 2` (or more including `TASK_START`), `"accepted"` ≥ 1, `"sourceTool": "CLAUDE_COWORK"`.

Then filter Tasks / Cost / Retries / MCP by **Claude Cowork**.

## Feature gates

`CLAUDE_COWORK` is a Tier-3 catalog tool. Ensure installation tier / phase flags allow Tier-3 collectors (same gates as other Tier-2+ tools in `FeatureGateService`).

## Related docs

- [Agentic fleet playbook](AGENTIC_FLEET_PLAYBOOK.md)
- External: [Cowork monitoring (OTel)](https://claude.com/docs/cowork/monitoring)
