--liquibase formatted sql
--changeset agent-retry:015-eval-phases
--comment Datasets, experiments, spans, feedback, and topic patterns

CREATE TABLE eval_datasets (
    id UUID PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    source VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    filter_json TEXT,
    created_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE eval_dataset_items (
    dataset_id UUID NOT NULL REFERENCES eval_datasets(id) ON DELETE CASCADE,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    added_at TIMESTAMP NOT NULL,
    PRIMARY KEY (dataset_id, task_id)
);

CREATE TABLE eval_experiments (
    id UUID PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    dataset_id UUID NOT NULL REFERENCES eval_datasets(id) ON DELETE CASCADE,
    baseline_definition_id UUID NOT NULL REFERENCES eval_definitions(id),
    variant_definition_id UUID NOT NULL REFERENCES eval_definitions(id),
    status VARCHAR(16) NOT NULL,
    notes TEXT,
    created_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);

CREATE TABLE eval_experiment_results (
    id UUID PRIMARY KEY,
    experiment_id UUID NOT NULL REFERENCES eval_experiments(id) ON DELETE CASCADE,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    variant VARCHAR(16) NOT NULL,
    eval_run_id UUID REFERENCES eval_runs(id) ON DELETE SET NULL,
    score DOUBLE PRECISION,
    label VARCHAR(16),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_eval_experiment_results_experiment ON eval_experiment_results (experiment_id);

CREATE TABLE spans (
    id UUID PRIMARY KEY,
    task_id UUID REFERENCES tasks(id) ON DELETE CASCADE,
    external_trace_id VARCHAR(128),
    external_span_id VARCHAR(128),
    parent_span_id UUID,
    name VARCHAR(255) NOT NULL,
    kind VARCHAR(64),
    start_at TIMESTAMP NOT NULL,
    duration_ms BIGINT NOT NULL,
    status VARCHAR(16),
    attributes TEXT,
    input_redacted TEXT,
    output_redacted TEXT,
    source_tool VARCHAR(64),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_spans_task_id ON spans (task_id);
CREATE INDEX idx_spans_start_at ON spans (start_at);
CREATE INDEX idx_spans_external_trace ON spans (external_trace_id);

CREATE TABLE session_feedback (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    event_id UUID REFERENCES task_events(id) ON DELETE SET NULL,
    rating SMALLINT NOT NULL,
    comment TEXT,
    feedback_type VARCHAR(32) NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_session_feedback_task ON session_feedback (task_id);

CREATE TABLE topic_patterns (
    id UUID PRIMARY KEY,
    label VARCHAR(255) NOT NULL,
    task_count INT NOT NULL,
    fail_rate DOUBLE PRECISION NOT NULL,
    sample_task_ids TEXT,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    computed_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_topic_patterns_computed ON topic_patterns (computed_at);
