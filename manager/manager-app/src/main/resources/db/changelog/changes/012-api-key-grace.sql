--liquibase formatted sql
--changeset agent-retry:012-api-key-grace
--comment Dual-key rotation grace window for overlapping AGENT keys
ALTER TABLE api_keys ADD COLUMN grace_until TIMESTAMP;
