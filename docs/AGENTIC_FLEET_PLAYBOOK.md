# Agentic fleet expansion playbook

How to add another coding/agentic product after Claude Cowork is green.

## Choose an ingest channel

| Channel | When to use |
|---------|-------------|
| **Local collector** (`SessionCollector` / JSONL / SQLite) | Tool writes durable logs on the developer machine (Claude Code, Cursor, Codex, …) |
| **OTel mapper** (like Cowork) | Vendor publishes OTLP with stable event names |
| **Vendor Analytics API** | Org-level adoption / billing reconciliation only — not a substitute for session replay |

## Checklist for a new tool

1. **Contract**
   - Add `SourceTool.<NAME>` in `shared/agent-contract`
   - Register in `SourceToolCatalog` (tier + path + `LogFormat` + capabilities)
   - Add GraphQL `SourceTool` enum value
   - Add UI label/color in `ui/manager-dashboard/src/sourceTools.ts` (+ chart colors)
   - Add MCP Zod enum in `mcp/packages/mcp-server/src/tools.ts`

2. **Ingest**
   - Collector bean **or** OTel mapper → `IngestBatchItem` (`TASK_START` / `TASK_EVENT` / `TOKEN_USAGE` / …)
   - Stable idempotency keys
   - Privacy: redact prompts by default

3. **Gates & ops**
   - Confirm `FeatureGateService` tier/phase allows the tool
   - Document admin setup (path, env, headers)
   - Golden fixture unit test for the mapper/parser

4. **Surface**
   - No new dashboard required — Cost / Retries / Replay / MCP / Insights already filter by `sourceTool`
   - Optionally mention the tool in chat tool-schema hints

## Tier-2 JSONL stubs (Windsurf, Cline, Continue, Aider, OpenCode)

Catalog entries exist. Before enabling collectors in production:

1. Capture real log samples from one developer machine
2. Add golden fixtures under collector tests
3. Flip collector enablement / feature gate only after parse accuracy is verified

## Do / don’t

- **Do** keep product identities separate (`CLAUDE_CODE` ≠ `CLAUDE_COWORK`)
- **Do** persist into the same task/token tables
- **Don’t** scrape undocumented disk paths when OTel or a documented log format exists
- **Don’t** require content capture for cost/retry KPIs
