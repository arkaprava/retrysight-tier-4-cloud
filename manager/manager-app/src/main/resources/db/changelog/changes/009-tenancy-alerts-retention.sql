--liquibase formatted sql
--changeset agent-retry:009-tenancy-alerts-retention
--comment Migrated from Flyway V9__tenancy_alerts_retention.sql
ALTER TABLE agents ADD COLUMN org_id UUID;
ALTER TABLE tasks ADD COLUMN org_id UUID;
ALTER TABLE api_keys ADD COLUMN org_id UUID;

CREATE INDEX idx_agents_org_id ON agents (org_id);
CREATE INDEX idx_tasks_org_id ON tasks (org_id);

CREATE TABLE alert_channels (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    target VARCHAR(1024) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE scheduled_reports (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    dataset VARCHAR(64) NOT NULL,
    format VARCHAR(16) NOT NULL,
    cron_expression VARCHAR(128) NOT NULL,
    email_to VARCHAR(1024),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_run_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE retention_settings (
    id UUID PRIMARY KEY,
    task_events_days INT NOT NULL DEFAULT 90,
    updated_at TIMESTAMP NOT NULL
);
