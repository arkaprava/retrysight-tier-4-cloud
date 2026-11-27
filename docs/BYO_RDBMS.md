# Bring-your-own RDBMS (enterprise)

Enterprises often require the manager to use **their** relational database instance (Oracle, SQL Server, MySQL, PostgreSQL on RDS/Aurora, etc.). This document describes the supported architecture and how to configure it.

## Architecture (vendor-neutral)

The capacity recommendation is **not Postgres-specific**:

| Layer | Role | Typical enterprise choice |
|-------|------|---------------------------|
| **OLTP RDBMS (BYO)** | Agents, users, SSO, tasks, budgets, idempotent ingest | Existing Postgres / MySQL / SQL Server / Oracle |
| **Analytics (optional at large scale)** | Event/token trends, cost KPIs | ClickHouse, warehouse, or RDBMS rollup tables |

Small/medium fleets can run OLTP + rollups on the same BYO RDBMS. Large fleets should keep the RDBMS for control-plane data and offload heavy analytics — see [CLICKHOUSE_ANALYTICS.md](CLICKHOUSE_ANALYTICS.md).

## What we made portable

| Area | Change |
|------|--------|
| Datasource | `SPRING_DATASOURCE_URL` / `USERNAME` / `PASSWORD` (Postgres env vars remain as defaults) |
| JDBC drivers | PostgreSQL + H2 shipped; add MySQL / SQL Server / Oracle drivers in your build or runtime image |
| Analytics SQL | `DATE_TRUNC` → ANSI `CAST(... AS DATE)`; `LIMIT` → JPA `setMaxResults` |
| Rollup column | Reserved `"day"` → `rollup_day` (Liquibase `013-portable-rollup-day`) |
| JPA entities | Removed `columnDefinition = "TEXT"` Postgres locks |

## Add a vendor JDBC driver

PostgreSQL is on the classpath by default. For other engines, add the driver to `manager/manager-app/pom.xml` (or drop the JAR into the runtime image):

```xml
<!-- MySQL 8+ -->
<dependency>
  <groupId>com.mysql</groupId>
  <artifactId>mysql-connector-j</artifactId>
  <scope>runtime</scope>
</dependency>

<!-- SQL Server -->
<dependency>
  <groupId>com.microsoft.sqlserver</groupId>
  <artifactId>mssql-jdbc</artifactId>
  <scope>runtime</scope>
</dependency>

<!-- Oracle: use your licensed ojdbc artifact from the corporate Maven repo -->
```

## Configure BYO database

Point the manager at your instance (no bundled DB required):

```bash
export SPRING_DATASOURCE_URL='jdbc:postgresql://db.corp.example:5432/agent_retry'
export SPRING_DATASOURCE_USERNAME='agent_retry_app'
export SPRING_DATASOURCE_PASSWORD='***'
```

### MySQL 8+

```bash
export SPRING_DATASOURCE_URL='jdbc:mysql://db.corp.example:3306/agent_retry?useSSL=true&serverTimezone=UTC'
export SPRING_DATASOURCE_USERNAME='agent_retry_app'
export SPRING_DATASOURCE_PASSWORD='***'
```

### SQL Server

```bash
export SPRING_DATASOURCE_URL='jdbc:sqlserver://db.corp.example:1433;databaseName=agent_retry;encrypt=true'
export SPRING_DATASOURCE_USERNAME='agent_retry_app'
export SPRING_DATASOURCE_PASSWORD='***'
```

### Oracle

Add your licensed JDBC driver to the runtime classpath, then:

```bash
export SPRING_DATASOURCE_URL='jdbc:oracle:thin:@//db.corp.example:1521/ORCLPDB1'
export SPRING_DATASOURCE_USERNAME='agent_retry_app'
export SPRING_DATASOURCE_PASSWORD='***'
# Optional if auto-detect fails:
# export SPRING_DATASOURCE_DRIVER=oracle.jdbc.OracleDriver
# export SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.OracleDialect
```

Grant the app user DDL (for Liquibase on first deploy) or DML-only after DBA applies migrations.

## Support matrix

| Engine | OLTP / Liquibase | Native analytics SQL | Notes |
|--------|------------------|----------------------|-------|
| **PostgreSQL 14+** | **Supported** (reference) | Supported | Default in Docker / local |
| **H2** (tests) | Supported (`dev-h2` profile) | Supported | CI / unit tests |
| **MySQL 8+** | **Best-effort** | Supported (ANSI casts) | Validate Liquibase on a staging DB; UUID/BOOLEAN mapping differs |
| **SQL Server 2019+** | **Best-effort** | Supported | Prefer Liquibase typed changesets for greenfield; UUID → `uniqueidentifier` |
| **Oracle 19c+** | **Planned / DBA-assisted** | Supported for day casts | Provide `ojdbc`; early migrations use PG-flavored SQL — DBA may translate DDL |

Historical Liquibase SQL (`001`–`012`) was authored for PostgreSQL/H2 (`UUID`, `BOOLEAN`, `IF NOT EXISTS`). For MySQL/SQL Server/Oracle **greenfield**, either:

1. Use PostgreSQL-compatible managed service (Aurora PostgreSQL, Cloud SQL Postgres) — simplest enterprise path, or  
2. Have DBA apply an equivalent schema and baseline Liquibase (`liquibase changelog-sync`), or  
3. Request vendor-specific changelog packs (follow-on).

Runtime analytics code paths are intentionally ANSI so the **application** is not Postgres-locked once the schema exists.

## Connection pooling

Size Hikari for concurrent UI + agent ingest (do not rely on the Spring Boot default of ~10 at large fleets):

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: ${DB_POOL_SIZE:40}
      minimum-idle: ${DB_POOL_MIN_IDLE:10}
```

## Security expectations (enterprise)

- Prefer private network / Private Link to the BYO instance  
- TLS on JDBC URLs  
- Least-privilege DB user (app + Liquibase roles separated if required)  
- Secrets via vault / cloud secret manager, not image env files  

## Related docs

- [CAPACITY.md](CAPACITY.md) — growth model and large-fleet guidance  
- [SECURITY.md](SECURITY.md) — auth / SSO  
