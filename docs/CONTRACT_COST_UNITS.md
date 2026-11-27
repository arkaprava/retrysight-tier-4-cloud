# Enterprise Contract Cost Units

Design for feeding contractual documents into cost analytics with dual-admin approval and tiered apply.

## Goals

- Enterprise admins upload **PDF / DOCX / TXT** contracts.
- Manager always parses on upload (auto-extract always on) and builds a simplified **cost unit analysis**.
- **SECADMIN** and **ADMIN** (distinct users) must both approve before anything is applied.
- **Tier 2**: upload, parse, review, dual-approve.
- **Tier 3+**: apply approved units to live pricing / billing (optional historical recompute, off by default).
- Multiple documents supported; **fail-safe** merge (conflicts never silent-apply).
- All contract bytes, text, and units stored in **Postgres**.

## Roles

| Role | UI access | Contract workflow |
|------|-----------|-------------------|
| `UI` | Standard analytics pages | No access |
| `ADMIN` | Ops pages relevant to billing/contracts (not Security/SSO) | Upload, review, edit units, **ADMIN** approval leg |
| `SECADMIN` | **Superuser — all pages** (Security, API Keys, Archive, Cost admin, contracts, etc.) | Upload, review, edit units, **SECADMIN** approval leg |

`SECADMIN` is the platform superuser and is never hidden from a page that any other role can see. Dual approval still requires a **distinct** `ADMIN` user for the other signature — a SECADMIN cannot self-approve both legs.

`ADMIN` is a first-class `UiUserRole` (local DB assignment initially; SSO `admin_group` mapping can follow).

## Tier gates

| Feature | Enum | Minimum tier | Requires |
|---------|------|--------------|----------|
| Review | `CONTRACT_COST_REVIEW` | `TIER_2_ANALYTICS` | `isCostEnabled()` |
| Apply | `CONTRACT_COST_APPLY` | `TIER_3_INTEGRATION` | review enabled |

Exposed on `platformFeatures`:

- `contractCostReviewEnabled`
- `contractCostApplyEnabled`

## Lifecycle

```text
UPLOADED → PARSING → PENDING_REVIEW → PARTIALLY_APPROVED
        → APPROVED_PENDING_APPLY   (Tier 2 may stop here)
        → APPLIED                  (Tier 3+ only)
        → REJECTED | FAILED | CONFLICT
```

## Cost units (approval artifact)

Fine print is collapsed into reviewable units:

| Type | Purpose |
|------|---------|
| `MODEL_RATE` | $/1M input, output, cache, reasoning per model key |
| `INCLUDED_ALLOWANCE` | seats / included tokens / credits |
| `OVERAGE_RATE` | overage after allowance |
| `PERIOD_COMMITMENT` | committed / billed USD for a period → `billing_actuals` |
| `DISCOUNT` / `MINIMUM` | only when confidently extracted |
| `UNRESOLVED` | held for human; never auto-applied |

Each unit: source contract id, excerpt, confidence, effective dates, status (`PROPOSED` / `CONFLICT` / `APPROVED` / `REJECTED`).

## Multi-document fail-safe

- Many contracts may be uploaded.
- Agreeing units merge; disagreeing units become `CONFLICT` and do not apply.
- Low confidence → `UNRESOLVED`.
- Partial apply allowed: only dual-approved, non-conflict units.

## Apply behavior (Tier 3+)

On apply after dual approval:

1. Upsert `MODEL_RATE` → `model_pricing`, then `ModelPricingService.refreshDbOverrides()`.
2. Insert `PERIOD_COMMITMENT` → `billing_actuals` with source `CONTRACT`.
3. Forward-only by default (new ingest uses new rates).
4. Historical recompute of `token_usage.estimated_cost_usd` / rollups is **opt-in**, off by default.

## Storage (DB)

Planned tables (future migration):

- `enterprise_contracts` — bytes, content type, sha256, status, uploader
- `contract_raw_text` — extracted text
- `cost_units` — simplified units + confidence + excerpt
- `cost_unit_approvals` — role, user, decision, timestamp
- `cost_unit_applications` — apply snapshot / links

## Security

- Tier + role checks on all APIs.
- MIME allowlist: `application/pdf`, DOCX, `text/plain`; size cap.
- No external LLM by default; optional local Ollama for fine-print enrichment (same posture as Insights chat).
- Full audit of upload / approve / reject / apply.

## Delivery slices

1. **ADMIN role + `CONTRACT_COST_REVIEW` / `CONTRACT_COST_APPLY` gates** (this slice)
2. Schema + upload + always-on extract → cost units
3. Multi-doc merge + conflict fail-safe
4. Dual-approval UI/API (Tier 2)
5. Apply forward-only + optional history (Tier 3+)
6. Ollama fine-print enrichment
