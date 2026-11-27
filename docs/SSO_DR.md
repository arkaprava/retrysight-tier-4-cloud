# SSO Disaster Recovery

## Symptom
No identity provider rows are configured, or the IdP is unreachable, and users cannot complete OIDC/SAML login.

## Recovery
1. Confirm the manager still accepts local secadmin login:
   - `POST /api/v1/auth/login` with `SECADMIN_EMAIL` / `SECADMIN_PASSWORD`
2. Sign in to the dashboard with secadmin credentials.
3. Open **Security** and either:
   - Fix issuer/client metadata and re-run **Test connection**, or
   - Delete the broken IdP config so local login remains the only path.
4. If JWT secret was rotated, force-refresh browser sessions (tokens from the old secret are invalid).

## Prevention
- Keep secadmin credentials in a secrets manager (never commit defaults to shared environments).
- Validate SSO in staging before enabling in production.
- Document a break-glass owner who knows the secadmin password location.
