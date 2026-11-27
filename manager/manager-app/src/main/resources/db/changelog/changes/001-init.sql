--liquibase formatted sql
--changeset agent-retry:001-init
--comment Migrated from Flyway V1__init.sql
CREATE TABLE agents (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    developer_email VARCHAR(255) NOT NULL,
    hostname VARCHAR(255) NOT NULL,
    api_key_hash VARCHAR(255),
    last_heartbeat_at TIMESTAMP,
    installed_collectors TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX idx_agents_hostname_email ON agents (hostname, developer_email);

CREATE TABLE tasks (
    id UUID PRIMARY KEY,
    agent_id UUID NOT NULL REFERENCES agents(id),
    external_session_id VARCHAR(512) NOT NULL,
    source_tool VARCHAR(32) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    metadata TEXT
);

CREATE INDEX idx_tasks_agent_id ON tasks (agent_id);
CREATE INDEX idx_tasks_started_at ON tasks (started_at);
CREATE INDEX idx_tasks_source_tool ON tasks (source_tool);

CREATE TABLE task_events (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES tasks(id),
    event_type VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    is_retry BOOLEAN NOT NULL DEFAULT FALSE,
    payload TEXT,
    sequence_num INT NOT NULL,
    idempotency_key VARCHAR(512) NOT NULL UNIQUE
);

CREATE INDEX idx_task_events_task_id ON task_events (task_id);

CREATE TABLE token_usage (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES tasks(id),
    turn_index INT NOT NULL,
    model VARCHAR(128),
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    cache_read_tokens BIGINT NOT NULL DEFAULT 0,
    cache_write_tokens BIGINT NOT NULL DEFAULT 0,
    reasoning_tokens BIGINT NOT NULL DEFAULT 0,
    estimated_cost_usd NUMERIC(12, 6),
    recorded_at TIMESTAMP NOT NULL,
    idempotency_key VARCHAR(512) NOT NULL UNIQUE
);

CREATE INDEX idx_token_usage_task_id ON token_usage (task_id);
