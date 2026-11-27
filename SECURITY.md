# Security Policy

## Supported versions

| Version | Supported |
|---------|-----------|
| 1.x     | Yes       |

## Reporting a vulnerability

Please **do not** open a public issue for security problems.

Report privately through GitHub: **Security → Report a vulnerability** on
[this repository](https://github.com/arkaprava/retrysight-tier-4-cloud/security/advisories/new), or email
[arkapravaghosh@hotmail.com](mailto:arkapravaghosh@hotmail.com) with a description, impact, steps to
reproduce and the affected version. We aim to acknowledge reports within a few business days.

## Deploying safely

- **Never run with the dev defaults** from `.env.example` (`changeme`, `dev-agent-key`, the default
  `JWT_SECRET` / `SSO_SECRET_KEY`) outside a throwaway local setup. With the `docker`, `staging` or
  `prod` Spring profiles the manager refuses to start on these values unless
  `SECURITY_ALLOW_DEFAULT_SECRETS=true`.
- The Helm chart generates strong secrets automatically; supply your own through values or an
  external secret manager if you prefer.
- Put the dashboard and manager behind TLS (ingress or reverse proxy); the bundled compose files
  are for local evaluation and do not terminate TLS.
- Keep PostgreSQL, ClickHouse and MinIO on a private network.

See [`docs/SECURITY.md`](docs/SECURITY.md) for the full hardening checklist.
