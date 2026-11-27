--liquibase formatted sql
--changeset agent-retry:006-task-git-context
--comment Migrated from Flyway V6__task_git_context.sql
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS project_root TEXT;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS repo_name TEXT;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS git_branch TEXT;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS git_commit TEXT;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS remote_url TEXT;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS remote_host TEXT;

CREATE INDEX IF NOT EXISTS idx_tasks_repo_name ON tasks (repo_name);
CREATE INDEX IF NOT EXISTS idx_tasks_git_branch ON tasks (git_branch);
CREATE INDEX IF NOT EXISTS idx_tasks_remote_host ON tasks (remote_host);
