# Security Analysis Checklist

Use this checklist during validation and before any shared/staging/production deploy.

## Threat model (summary)

| Asset | Threat | Severity |
|-------|--------|----------|
| JWT / MCP tokens | Theft, long-lived MCP tokens (default 30d via MCP_TOKEN_EXPIRATION_DAYS) | High |
| Agent API key | Fake ingest / DoS | High |
| Postgres credentials | Data exposure | High |
| GraphQL | Over-fetch; org-wide visibility | Medium |
| SSO client secrets | Weak encryption key | High (if SSO) |
| GraphiQL / CORS | Prod attack surface | Medium |
| Task payloads / git paths | Sensitive path leakage | Medium |

## Checklist

### Authentication & authorization

- [ ] Bad password → 401 on `/api/v1/auth/login`
- [ ] Expired / forged JWT → 401 on `/graphql`
- [ ] Missing `X-Agent-Api-Key` → 401 on `/api/v1/ingest/*`
- [ ] Wrong agent key → 401
- [ ] UI role cannot call SECADMIN-only mutations (budgets when restricted, SSO admin)
- [ ] SECADMIN (superuser) can open every UI page; ADMIN cannot open Security/SSO admin routes
- [ ] ADMIN can use contract review APIs; SECADMIN can use those plus all superuser APIs
- [ ] Contract cost apply rejected on Tier 2; allowed only on Tier 3+ after dual approval (distinct ADMIN + SECADMIN)
- [ ] MCP token cannot escalate beyond issuer roles
- [ ] SSO disaster recovery: no IdP rows → local secadmin login available

### Secrets (must change for production)

| Secret | Default (dev) | Env override |
|--------|---------------|--------------|
| JWT signing | `agent-retry-platform-dev-secret-key-32chars` | `JWT_SECRET` |
| Secadmin password | `changeme` | `SECADMIN_PASSWORD` |
| SSO encryption | `dev-sso-secret-encryption-key-32` | `SSO_SECRET_KEY` |
| Agent API key | `dev-agent-key` | Manager seed/sync: `AGENT_API_KEY` / `manager.auth.agent-api-key`; agent client: `AGENT_MANAGER_API_KEY` or `AGENT_API_KEY` |
| Postgres | `agent_retry` / `agent_retry` | compose env |

- [ ] All defaults rotated in staging/prod (`JWT_SECRET`, `SECADMIN_PASSWORD`, `SSO_SECRET_KEY`, `AGENT_API_KEY` / Postgres) — never ship `changeme` or `dev-*` defaults
- [ ] Secrets not logged (spot-check manager/agent logs)
- [ ] After `rotateApiKey`, roll agent configs during the grace window (`manager.auth.api-key-rotation-grace-seconds`, default 24h) so old keys keep working until cutover
- [ ] Prefer a fleet-wide `AGENT_API_KEY` for enrollment; per-agent binding via `agents.api_key_hash` is not enforced yet — treat the shared AGENT key as the trust boundary

### Agent → manager transport

- Agent WebClient uses the JVM default trust store for HTTPS.
- Optional HTTP proxy: `agent.manager.proxy-host` / `proxy-port` (and optional username/password), or standard `HTTP_PROXY` / `HTTPS_PROXY` env vars.
- Durable device identity: agents persist manager-assigned UUID in `~/.agent-retry/agent-id` and send `X-Agent-Id` on subsequent ingest calls (hostname+email remains enrollment fallback only).

### Exposure

- [ ] Production profile disables GraphiQL (`spring.profiles.active=prod` or compose override)
- [ ] CORS origins restricted to real UI origins (`CORS_ALLOWED_ORIGINS`)
- [ ] TLS terminated in front of manager (HTTPS)
- [ ] GraphiQL not reachable from public internet

### Privacy

- [ ] When `manager.platform.privacy.redact-project-root=true` and git enterprise enabled, `projectRoot` redacted in API
- [ ] Agent `privacy.store-full-prompts` left false unless explicitly required
- [ ] Insights chat uses local Ollama only (`OLLAMA_BASE_URL`); do not point at remote LLM hosts without review — see [CHATBOT.md](CHATBOT.md)

### Dependencies

```bash
cd mcp && npm audit --omit=dev
cd ui/manager-dashboard && npm audit --omit=dev
# Optional: OWASP dependency-check on Maven modules
```

- [ ] No untriaged critical CVEs

### CSRF / session

CSRF is disabled; APIs expect Bearer JWT or API keys. Cookie-based SSO redirects must keep redirect URIs locked to known origins (`SSO_CALLBACK_BASE_URL`).

## Production hardening profile

Activate Spring profile `prod` on the manager (see `application.yml`):

- GraphiQL disabled  
- Expect externalized `JWT_SECRET`, `SECADMIN_PASSWORD`, `SSO_SECRET_KEY`  

## Findings template

| ID | Severity | Finding | Evidence | Remediation | Status |
|----|----------|---------|----------|-------------|--------|
| SEC-001 | | | | | Open |

## Pass criteria

- All High items remediated or formally accepted  
- Secrets rotated for non-local envs  
- GraphiQL off in prod  
- Authn/authz negative tests recorded (smoke script + manual SECADMIN checks)  
