# Agent Performance Monitor — MCP Integration

Model Context Protocol (MCP) server for querying retry metrics, tasks, agents, and reports from IDEs (Cursor, VS Code, Kiro).

## Packages

| Package | Description |
|---------|-------------|
| `@agentretry/mcp-core` | Auth, GraphQL client, reusable tool registry |
| `@agentretry/mcp-server` | stdio MCP server entrypoint |

## Prerequisites

- Node.js 20+
- Manager running at `http://localhost:28477` (or set `MANAGER_URL`)
- UI credentials or an MCP token

## Quick start

```bash
cd mcp
npm install
npm run build
```

### Option A — long-lived MCP token (recommended)

```bash
curl -s -X POST http://localhost:28477/api/v1/auth/mcp-token \
  -H 'Content-Type: application/json' \
  -d '{"email":"secadmin@local","password":"changeme"}'
```

Set `APM_MCP_TOKEN` to the returned token (valid 90 days by default).

### Option B — email + password

Set `APM_EMAIL` and `APM_PASSWORD`. The server exchanges them for an MCP token on startup.

## Run locally

```bash
MANAGER_URL=http://localhost:28477 \
APM_EMAIL=secadmin@local \
APM_PASSWORD=changeme \
npm run start
```

## IDE setup

### Cursor

Copy `ide-config/cursor.mcp.json` into `.cursor/mcp.json` (or merge the `agent-performance` entry). Replace `ABSOLUTE_PATH` with your repo path.

### VS Code

Use `ide-config/vscode.mcp.json` as a reference for MCP server configuration in VS Code MCP settings.

### Kiro

Use `ide-config/kiro.mcp.json` — same stdio pattern as Cursor.

## Environment variables

| Variable | Required | Description |
|----------|----------|-------------|
| `MANAGER_URL` | No | Manager base URL (default `http://localhost:28477`) |
| `APM_MCP_TOKEN` | One of token or email/password | Long-lived JWT from `/api/v1/auth/mcp-token` |
| `APM_EMAIL` | With password | Login email |
| `APM_PASSWORD` | With email | Login password |

## Tools

| Tool | Description |
|------|-------------|
| `get_dashboard_summary` | KPIs, retry cost, trends |
| `list_tasks` | Paginated tasks with filters |
| `get_task` | Task detail + timeline |
| `list_agents` | Collector fleet status |
| `list_users` | Distinct developers |
| `generate_report` | CSV/JSON report export |

## Resources

| URI | Content |
|-----|---------|
| `agent-performance://dashboard/summary` | Latest dashboard JSON |
| `agent-performance://agents` | Agent list |
| `agent-performance://users` | User summaries |
| `agent-performance://tasks/{id}` | Task detail |

## Prompts

| Prompt | Purpose |
|--------|---------|
| `analyze-retry-session` | Deep-dive a task's retry timeline |
| `compare-tools` | Compare retry/token metrics across AI tools |

## Tests

```bash
npm run test -w @agentretry/mcp-core
```

## Example IDE questions

- "What are my top retry tasks this week?"
- "Show dashboard summary filtered to user alice"
- "List collectors that haven't sent a heartbeat recently"
- "Generate a JSON task list report for the last 7 days"

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `Cannot reach the manager` | Start manager: `mvn -pl manager/manager-app spring-boot:run` |
| `Authentication failed` | Refresh MCP token or verify credentials |
| `Access denied (403)` | Token needs `UI` or `SECADMIN` role |
