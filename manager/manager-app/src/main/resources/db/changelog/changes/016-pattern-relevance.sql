--liquibase formatted sql
--changeset agent-retry:016-pattern-relevance
--comment Suspicious / non-engineering flags for topic patterns

ALTER TABLE topic_patterns ADD COLUMN relevance_score DOUBLE PRECISION NOT NULL DEFAULT 0.5;
ALTER TABLE topic_patterns ADD COLUMN suspicious BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE topic_patterns ADD COLUMN flag_reason VARCHAR(512);
