# Insights Chatbot (local AI)

In-app Insights chat answers questions about users, tools, retries, cost, and tasks using **your manager data** and a **local LLM** (Ollama). No prompts are sent to external cloud AI by default.

## Architecture

```
UI drawer  →  POST /api/v1/chat (SSE, JWT)
                →  ChatOrchestrator tool loop
                     →  Ollama /v1/chat/completions (tools)
                     →  ChatToolRegistry → QueryService / Cost / Retry / Replay / Reports
```

If Ollama is down and `manager.chat.allow-deterministic-fallback=true` (default), the manager runs a keyword→tool planner and returns structured insights without an LLM.

## Setup

1. Install [Ollama](https://ollama.com/) and pull a tool-capable model:

```bash
ollama pull llama3.2
# or: ollama pull qwen2.5:7b
```

2. Configure manager (env or `application.yml`):

| Variable | Default | Purpose |
|----------|---------|---------|
| `CHAT_ENABLED` | `true` | Feature flag |
| `OLLAMA_BASE_URL` | `http://127.0.0.1:11434` | Ollama base URL |
| `CHAT_MODEL` | `llama3.2` | Model name |
| `CHAT_RPM` | `30` | Per-user requests/minute |
| `CHAT_DETERMINISTIC_FALLBACK` | `true` | Tool-only mode when Ollama is down |

3. Open the dashboard → **Insights** (bottom-right) or press **⌘J / Ctrl+J**.

## API

| Method | Path | Notes |
|--------|------|-------|
| `GET` | `/api/v1/chat/health` | Ollama reachability + model list |
| `GET` | `/api/v1/chat/suggestions?path=` | Page-aware prompt chips |
| `GET` | `/api/v1/chat/tools` | Tool schemas |
| `GET` | `/api/v1/chat/audit` | Recent chat audit entries (in-memory) |
| `POST` | `/api/v1/chat` | SSE stream (`status`, `tool_*`, `delta`, `final`, `done`) |
| `POST` | `/api/v1/chat/message` | Synchronous JSON completion |

All routes require JWT (`ROLE_UI` or `ROLE_SECADMIN`).

## Tools

Aligned with MCP-style analytics:

- `get_dashboard_summary`
- `get_cost_summary`
- `get_retry_analysis`
- `list_tasks` / `get_task`
- `get_session_replay_prompt` (PROMPT format, redacted)
- `list_agents` / `list_users`
- `generate_report_preview`

## Privacy

Before LLM / audit:

- API keys, Bearer tokens, emails, and absolute paths are redacted
- Tool results are truncated (`max-tool-result-chars`)
- Audit logs store principal, mode, tools, path — not full prompts by default (lengths only)

Do not enable remote OpenAI-compatible endpoints unless you accept data leaving the host.

## Ops

- Rate limit: `manager.chat.requests-per-minute`
- Health: `GET /api/v1/chat/health` → `ready` / `degraded` / `unavailable`
- Prefer models with reliable tool calling for best answers

## Phases delivered

1. Chat shell + SSE + tool registry against live services  
2. Ollama client + privacy redaction + deterministic fallback  
3. Page context, suggested prompts, deep links in answers  
4. Health, rate limits, audit trail, docs, golden planning tests  
