--liquibase formatted sql
--changeset agent-retry:003-agent-user-info
--comment Migrated from Flyway V3__agent_user_info.sql
ALTER TABLE agents ADD COLUMN os_username VARCHAR(255);
ALTER TABLE agents ADD COLUMN display_name VARCHAR(255);
ALTER TABLE agents ADD COLUMN git_email VARCHAR(255);

CREATE INDEX idx_agents_os_username ON agents (os_username);
CREATE INDEX idx_agents_display_name ON agents (display_name);
