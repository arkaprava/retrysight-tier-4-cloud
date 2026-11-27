--liquibase formatted sql
--changeset agent-retry:010-eval-scorecards
--comment Migrated from Flyway V10__eval_scorecards.sql
CREATE TABLE eval_scorecards (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    score DOUBLE PRECISION NOT NULL,
    notes TEXT,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_eval_scorecards_task_id ON eval_scorecards (task_id);
