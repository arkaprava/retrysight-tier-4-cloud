--liquibase formatted sql
--changeset agent-retry:014-evaluations
--comment Eval definitions, automated runs, and annotation queue

CREATE TABLE eval_definitions (
    id UUID PRIMARY KEY,
    slug VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    prompt_template TEXT,
    rule_config TEXT,
    target_scope VARCHAR(32) NOT NULL DEFAULT 'TASK',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE eval_runs (
    id UUID PRIMARY KEY,
    definition_id UUID NOT NULL REFERENCES eval_definitions(id) ON DELETE CASCADE,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    event_id UUID REFERENCES task_events(id) ON DELETE SET NULL,
    score DOUBLE PRECISION,
    label VARCHAR(16),
    explanation TEXT,
    status VARCHAR(16) NOT NULL,
    triggered_by VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_eval_runs_task_id ON eval_runs (task_id);
CREATE INDEX idx_eval_runs_definition_id ON eval_runs (definition_id);
CREATE INDEX idx_eval_runs_created_at ON eval_runs (created_at);

CREATE TABLE annotation_queue_items (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    reason VARCHAR(64) NOT NULL,
    assigned_to VARCHAR(255),
    status VARCHAR(16) NOT NULL,
    notes TEXT,
    created_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);

CREATE INDEX idx_annotation_queue_status ON annotation_queue_items (status);
CREATE INDEX idx_annotation_queue_task_id ON annotation_queue_items (task_id);
