--liquibase formatted sql
--changeset agent-retry:011-archive-performance-rollups
--comment Migrated from Flyway V11__archive_performance_rollups.sql
-- Performance indexes for common analytics / purge paths
CREATE INDEX IF NOT EXISTS idx_tasks_started_source ON tasks (started_at, source_tool);
CREATE INDEX IF NOT EXISTS idx_task_events_task_retry ON task_events (task_id, is_retry);
CREATE INDEX IF NOT EXISTS idx_token_usage_task_recorded ON token_usage (task_id, recorded_at);

-- Archive policy (singleton row id fixed in app)
-- IF NOT EXISTS: safe after partial apply + flyway.repair() remigration (esp. H2 tests)
CREATE TABLE IF NOT EXISTS archive_policy (
    id UUID PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    mode VARCHAR(32) NOT NULL DEFAULT 'DELETE_ONLY',
    schedule_cron VARCHAR(128) NOT NULL DEFAULT '0 30 2 * * *',
    task_events_days INT NOT NULL DEFAULT 90,
    token_usage_days INT NOT NULL DEFAULT 90,
    task_headers_days INT NOT NULL DEFAULT 365,
    datasets VARCHAR(255) NOT NULL DEFAULT 'TASK_EVENTS,TOKEN_USAGE',
    destination_type VARCHAR(32) NOT NULL DEFAULT 'LOCAL_FS',
    local_path VARCHAR(1024),
    network_path VARCHAR(1024),
    s3_bucket VARCHAR(255),
    s3_prefix VARCHAR(512),
    s3_region VARCHAR(64),
    s3_endpoint VARCHAR(512),
    s3_access_key_env VARCHAR(255),
    s3_secret_key_env VARCHAR(255),
    legal_hold BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS archive_runs (
    id UUID PRIMARY KEY,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    status VARCHAR(32) NOT NULL,
    dry_run BOOLEAN NOT NULL DEFAULT FALSE,
    mode VARCHAR(32),
    destination_type VARCHAR(32),
    rows_exported BIGINT NOT NULL DEFAULT 0,
    rows_deleted BIGINT NOT NULL DEFAULT 0,
    bytes_written BIGINT NOT NULL DEFAULT 0,
    datasets VARCHAR(255),
    error_message TEXT,
    manifest_summary TEXT
);

CREATE INDEX IF NOT EXISTS idx_archive_runs_started ON archive_runs (started_at);

-- Daily rollups for trend charts over long ranges
-- Column "day" renamed to rollup_day in 013-portable-rollup-day (cross-RDBMS)
CREATE TABLE IF NOT EXISTS daily_cost_rollups (
    "day" DATE NOT NULL,
    source_tool VARCHAR(32) NOT NULL,
    model VARCHAR(128) NOT NULL,
    cost_usd NUMERIC(14, 6) NOT NULL DEFAULT 0,
    retry_cost_usd NUMERIC(14, 6) NOT NULL DEFAULT 0,
    retry_count BIGINT NOT NULL DEFAULT 0,
    turn_count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY ("day", source_tool, model)
);

CREATE TABLE IF NOT EXISTS daily_retry_rollups (
    "day" DATE NOT NULL,
    source_tool VARCHAR(32) NOT NULL,
    model VARCHAR(128) NOT NULL,
    retry_count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY ("day", source_tool, model)
);

CREATE INDEX IF NOT EXISTS idx_daily_cost_rollups_day ON daily_cost_rollups ("day");
CREATE INDEX IF NOT EXISTS idx_daily_retry_rollups_day ON daily_retry_rollups ("day");

-- Partition maintenance registry (logical periods; physical PG partitions optional)
CREATE TABLE IF NOT EXISTS hot_data_partitions (
    id UUID PRIMARY KEY,
    dataset VARCHAR(64) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    notes TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_hot_data_partitions_dataset ON hot_data_partitions (dataset, period_start);

-- Seed policy from existing retention defaults when present
INSERT INTO archive_policy (
    id, enabled, mode, schedule_cron, task_events_days, token_usage_days, task_headers_days,
    datasets, destination_type, local_path, legal_hold, updated_at
)
SELECT
    '00000000-0000-0000-0000-000000000002',
    TRUE,
    'DELETE_ONLY',
    '0 30 2 * * *',
    COALESCE((SELECT task_events_days FROM retention_settings LIMIT 1), 90),
    90,
    365,
    'TASK_EVENTS,TOKEN_USAGE',
    'LOCAL_FS',
    '~/.agent-retry/archive',
    FALSE,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM archive_policy WHERE id = '00000000-0000-0000-0000-000000000002'
);
