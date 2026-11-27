--liquibase formatted sql
--changeset agent-retry:004-sso
--comment Migrated from Flyway V4__sso.sql
ALTER TABLE ui_users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE ui_users ADD COLUMN role VARCHAR(32) NOT NULL DEFAULT 'UI';
ALTER TABLE ui_users ADD COLUMN auth_provider VARCHAR(32) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE ui_users ADD COLUMN external_subject VARCHAR(512);

CREATE UNIQUE INDEX idx_ui_users_external_subject ON ui_users (external_subject);

CREATE TABLE identity_provider_config (
    id UUID PRIMARY KEY,
    provider_type VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    validated_at TIMESTAMP,
    issuer_uri VARCHAR(1024),
    client_id VARCHAR(512),
    client_secret_enc TEXT,
    scopes VARCHAR(512),
    redirect_uri VARCHAR(1024),
    idp_metadata_url VARCHAR(2048),
    idp_metadata_xml TEXT,
    entity_id VARCHAR(512),
    acs_url VARCHAR(1024),
    email_attribute VARCHAR(128),
    display_name_attribute VARCHAR(128),
    groups_attribute VARCHAR(128),
    superuser_group VARCHAR(256),
    created_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
