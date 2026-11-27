--liquibase formatted sql
--changeset agent-retry:007-unique-task-session
--comment Migrated from Flyway V7__unique_task_session.sql
-- Collapse duplicate tasks for the same IDE session before enforcing uniqueness.
-- Keep the copy with the most events (then earliest start, then id).
-- Written to work on both PostgreSQL and H2 (PostgreSQL compatibility mode).

DELETE FROM token_usage
WHERE task_id IN (
    SELECT id FROM (
        SELECT t.id AS id,
               ROW_NUMBER() OVER (
                   PARTITION BY t.source_tool, t.external_session_id
                   ORDER BY (SELECT COUNT(*) FROM task_events e WHERE e.task_id = t.id) DESC,
                            COALESCE(t.started_at, TIMESTAMP '1970-01-01 00:00:00') ASC,
                            t.id ASC
               ) AS rn
        FROM tasks t
    ) ranked
    WHERE rn > 1
);

DELETE FROM task_events
WHERE task_id IN (
    SELECT id FROM (
        SELECT t.id AS id,
               ROW_NUMBER() OVER (
                   PARTITION BY t.source_tool, t.external_session_id
                   ORDER BY (SELECT COUNT(*) FROM task_events e WHERE e.task_id = t.id) DESC,
                            COALESCE(t.started_at, TIMESTAMP '1970-01-01 00:00:00') ASC,
                            t.id ASC
               ) AS rn
        FROM tasks t
    ) ranked
    WHERE rn > 1
);

DELETE FROM tasks
WHERE id IN (
    SELECT id FROM (
        SELECT t.id AS id,
               ROW_NUMBER() OVER (
                   PARTITION BY t.source_tool, t.external_session_id
                   ORDER BY (SELECT COUNT(*) FROM task_events e WHERE e.task_id = t.id) DESC,
                            COALESCE(t.started_at, TIMESTAMP '1970-01-01 00:00:00') ASC,
                            t.id ASC
               ) AS rn
        FROM tasks t
    ) ranked
    WHERE rn > 1
);

CREATE UNIQUE INDEX idx_tasks_source_tool_external_session
    ON tasks (source_tool, external_session_id);

-- Maps collector-local / deterministic task ids onto the canonical row for a session.
CREATE TABLE task_id_aliases (
    alias_id UUID PRIMARY KEY,
    canonical_task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE
);

CREATE INDEX idx_task_id_aliases_canonical ON task_id_aliases (canonical_task_id);
