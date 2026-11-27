FROM postgres:16-alpine

ENV POSTGRES_DB=agent_retry
ENV POSTGRES_USER=agent_retry

COPY init/ /docker-entrypoint-initdb.d/

HEALTHCHECK --interval=5s --timeout=5s --retries=10 \
  CMD pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"
