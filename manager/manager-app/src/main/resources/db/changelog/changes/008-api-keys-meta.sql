--liquibase formatted sql
--changeset agent-retry:008-api-keys-meta
--comment Migrated from Flyway V8__api_keys_meta.sql
ALTER TABLE api_keys ADD COLUMN key_prefix VARCHAR(16);
ALTER TABLE api_keys ADD COLUMN last_used_at TIMESTAMP;
ALTER TABLE api_keys ADD COLUMN revoked_at TIMESTAMP;

CREATE INDEX idx_task_events_occurred_at ON task_events (occurred_at);
