--liquibase formatted sql
--changeset agent-retry:005-cost-tracking
--comment Migrated from Flyway V5__cost_tracking.sql
-- Phase 1-6: LLM cost tracking foundation

ALTER TABLE token_usage ADD COLUMN token_source VARCHAR(16) NOT NULL DEFAULT 'ESTIMATED';

CREATE INDEX idx_token_usage_recorded_at ON token_usage (recorded_at);

CREATE TABLE model_pricing (
    model_key VARCHAR(128) PRIMARY KEY,
    input_per_million NUMERIC(12, 6) NOT NULL,
    output_per_million NUMERIC(12, 6) NOT NULL,
    cache_read_per_million NUMERIC(12, 6) NOT NULL DEFAULT 0,
    cache_write_per_million NUMERIC(12, 6) NOT NULL DEFAULT 0,
    reasoning_per_million NUMERIC(12, 6) NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE cost_budgets (
    id UUID PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    scope VARCHAR(32) NOT NULL,
    scope_value VARCHAR(256),
    period VARCHAR(32) NOT NULL,
    limit_usd NUMERIC(12, 6) NOT NULL,
    alert_threshold_pct NUMERIC(5, 2) NOT NULL DEFAULT 80,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE cost_alerts (
    id UUID PRIMARY KEY,
    budget_id UUID NOT NULL REFERENCES cost_budgets(id) ON DELETE CASCADE,
    triggered_at TIMESTAMP NOT NULL,
    spend_usd NUMERIC(12, 6) NOT NULL,
    limit_usd NUMERIC(12, 6) NOT NULL,
    message TEXT NOT NULL,
    resolved_at TIMESTAMP
);

CREATE INDEX idx_cost_alerts_budget_id ON cost_alerts (budget_id);
CREATE INDEX idx_cost_alerts_resolved_at ON cost_alerts (resolved_at);

CREATE TABLE billing_actuals (
    id UUID PRIMARY KEY,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    actual_cost_usd NUMERIC(12, 6) NOT NULL,
    source VARCHAR(32) NOT NULL,
    notes TEXT,
    imported_at TIMESTAMP NOT NULL,
    imported_by VARCHAR(256) NOT NULL
);

CREATE INDEX idx_billing_actuals_period ON billing_actuals (period_start, period_end);

-- Seed default pricing tiers (overridable via model_pricing table)
INSERT INTO model_pricing (model_key, input_per_million, output_per_million, cache_read_per_million, cache_write_per_million, reasoning_per_million, updated_at) VALUES
    ('claude-opus', 5.0, 25.0, 0.50, 6.25, 25.0, NOW()),
    ('claude-sonnet', 3.0, 15.0, 0.30, 3.75, 15.0, NOW()),
    ('claude-haiku', 1.0, 5.0, 0.10, 1.25, 5.0, NOW()),
    ('claude-opus-legacy', 15.0, 75.0, 1.50, 18.75, 75.0, NOW()),
    ('gpt-4o', 2.50, 10.0, 1.25, 2.50, 10.0, NOW()),
    ('gpt-4o-mini', 0.15, 0.60, 0.075, 0.15, 0.60, NOW()),
    ('gpt-4.1', 2.0, 8.0, 0.50, 2.0, 8.0, NOW()),
    ('gpt-4.1-mini', 0.40, 1.60, 0.10, 0.40, 1.60, NOW()),
    ('gpt-5', 1.25, 10.0, 0.125, 1.5625, 10.0, NOW()),
    ('gpt-5.3-codex', 1.75, 14.0, 0.175, 2.1875, 14.0, NOW()),
    ('o1', 15.0, 60.0, 7.50, 15.0, 60.0, NOW()),
    ('o3-mini', 1.10, 4.40, 0.55, 1.10, 4.40, NOW()),
    ('o3', 2.0, 8.0, 0.20, 2.50, 8.0, NOW()),
    ('cursor-auto', 1.25, 6.0, 0.25, 1.25, 6.0, NOW()),
    ('composer-2.5', 0.50, 2.50, 0.125, 0.50, 2.50, NOW()),
    ('composer-2.5-fast', 3.0, 15.0, 0.30, 3.0, 15.0, NOW()),
    ('composer-1', 1.25, 10.0, 0.125, 1.25, 10.0, NOW()),
    ('default', 3.0, 15.0, 0.30, 3.75, 15.0, NOW());
