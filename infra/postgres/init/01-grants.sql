-- Ensure the application user owns the public schema objects created by Flyway.
GRANT ALL PRIVILEGES ON DATABASE agent_retry TO agent_retry;
GRANT ALL PRIVILEGES ON SCHEMA public TO agent_retry;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO agent_retry;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO agent_retry;
