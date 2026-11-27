/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.repository;

import com.agentretry.manager.persistence.DatabaseSqlDialect;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class CostAnalyticsRepository {

    private final DatabaseSqlDialect sqlDialect;

    @PersistenceContext
    private EntityManager entityManager;

    public CostAnalyticsRepository(DatabaseSqlDialect sqlDialect) {
        this.sqlDialect = sqlDialect;
    }

    public double sumTotalCost(Instant from, Instant to, String user, String sourceTool) {
        QueryParts parts = buildFilter(from, to, user, sourceTool);
        String sql = """
                SELECT COALESCE(SUM(tu.estimated_cost_usd), 0)
                FROM token_usage tu
                JOIN tasks t ON t.id = tu.task_id
                JOIN agents a ON a.id = t.agent_id
                WHERE 1=1
                %s
                """.formatted(parts.whereClause());

        var query = entityManager.createNativeQuery(sql);
        parts.bindParams(query);
        Object result = query.getSingleResult();
        return toDouble(result);
    }

    public List<Map.Entry<String, Double>> aggregateCostByTool(Instant from, Instant to, String user, String sourceTool) {
        QueryParts parts = buildFilter(from, to, user, sourceTool);
        String sql = """
                SELECT t.source_tool AS metric_key, COALESCE(SUM(tu.estimated_cost_usd), 0) AS metric_value
                FROM token_usage tu
                JOIN tasks t ON t.id = tu.task_id
                JOIN agents a ON a.id = t.agent_id
                WHERE 1=1
                %s
                GROUP BY t.source_tool
                ORDER BY metric_value DESC
                """.formatted(parts.whereClause());

        return runKeyValueQuery(sql, parts);
    }

    public List<Map.Entry<String, Double>> aggregateCostByModel(Instant from, Instant to, String user, String sourceTool) {
        QueryParts parts = buildFilter(from, to, user, sourceTool);
        String sql = """
                SELECT COALESCE(tu.model, 'unknown') AS metric_key, COALESCE(SUM(tu.estimated_cost_usd), 0) AS metric_value
                FROM token_usage tu
                JOIN tasks t ON t.id = tu.task_id
                JOIN agents a ON a.id = t.agent_id
                WHERE 1=1
                %s
                GROUP BY COALESCE(tu.model, 'unknown')
                ORDER BY metric_value DESC
                """.formatted(parts.whereClause());

        return runKeyValueQuery(sql, parts);
    }

    public List<UserCostRow> aggregateCostByUser(Instant from, Instant to, String user, String sourceTool) {
        QueryParts parts = buildFilter(from, to, user, sourceTool);
        String sql = """
                SELECT a.developer_email AS email,
                       MAX(a.display_name) AS display_name,
                       COALESCE(SUM(tu.estimated_cost_usd), 0) AS metric_value
                FROM token_usage tu
                JOIN tasks t ON t.id = tu.task_id
                JOIN agents a ON a.id = t.agent_id
                WHERE 1=1
                %s
                GROUP BY a.developer_email
                ORDER BY metric_value DESC
                """.formatted(parts.whereClause());

        var query = entityManager.createNativeQuery(sql);
        parts.bindParams(query);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<UserCostRow> results = new ArrayList<>();
        for (Object[] row : rows) {
            results.add(new UserCostRow(
                    row[0] != null ? row[0].toString() : "unknown",
                    row[1] != null ? row[1].toString() : null,
                    toDouble(row[2])
            ));
        }
        return results;
    }

    public List<Map.Entry<Instant, Double>> aggregateCostTrend(Instant from, Instant to, String user, String sourceTool) {
        QueryParts parts = buildFilter(from, to, user, sourceTool);
        String dayExpr = sqlDialect.castToDate("tu.recorded_at");
        String sql = """
                SELECT %s AS trend_day, COALESCE(SUM(tu.estimated_cost_usd), 0) AS metric_value
                FROM token_usage tu
                JOIN tasks t ON t.id = tu.task_id
                JOIN agents a ON a.id = t.agent_id
                WHERE 1=1
                %s
                GROUP BY %s
                ORDER BY trend_day ASC
                """.formatted(dayExpr, parts.whereClause(), dayExpr);

        var query = entityManager.createNativeQuery(sql);
        parts.bindParams(query);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<Map.Entry<Instant, Double>> results = new ArrayList<>();
        for (Object[] row : rows) {
            results.add(Map.entry(toDayInstant(row[0]), toDouble(row[1])));
        }
        return results;
    }

    public List<SeriesTrendRow> aggregateCostTrendByTool(Instant from, Instant to, String user, String sourceTool) {
        QueryParts parts = buildFilter(from, to, user, sourceTool);
        String dayExpr = sqlDialect.castToDate("tu.recorded_at");
        String sql = """
                SELECT %s AS trend_day,
                       t.source_tool AS series_name,
                       COALESCE(SUM(tu.estimated_cost_usd), 0) AS metric_value
                FROM token_usage tu
                JOIN tasks t ON t.id = tu.task_id
                JOIN agents a ON a.id = t.agent_id
                WHERE 1=1
                %s
                GROUP BY %s, t.source_tool
                ORDER BY trend_day ASC, series_name ASC
                """.formatted(dayExpr, parts.whereClause(), dayExpr);
        return runSeriesTrendQuery(sql, parts);
    }

    public List<SeriesTrendRow> aggregateCostTrendByModel(Instant from, Instant to, String user, String sourceTool) {
        QueryParts parts = buildFilter(from, to, user, sourceTool);
        String dayExpr = sqlDialect.castToDate("tu.recorded_at");
        String sql = """
                SELECT %s AS trend_day,
                       COALESCE(tu.model, 'unknown') AS series_name,
                       COALESCE(SUM(tu.estimated_cost_usd), 0) AS metric_value
                FROM token_usage tu
                JOIN tasks t ON t.id = tu.task_id
                JOIN agents a ON a.id = t.agent_id
                WHERE 1=1
                %s
                GROUP BY %s, COALESCE(tu.model, 'unknown')
                ORDER BY trend_day ASC, series_name ASC
                """.formatted(dayExpr, parts.whereClause(), dayExpr);
        return runSeriesTrendQuery(sql, parts);
    }

    private List<SeriesTrendRow> runSeriesTrendQuery(String sql, QueryParts parts) {
        var query = entityManager.createNativeQuery(sql);
        parts.bindParams(query);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<SeriesTrendRow> results = new ArrayList<>();
        for (Object[] row : rows) {
            results.add(new SeriesTrendRow(
                    toDayInstant(row[0]),
                    row[1] != null ? row[1].toString() : "unknown",
                    toDouble(row[2])
            ));
        }
        return results;
    }

    private static Instant toDayInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant.truncatedTo(ChronoUnit.DAYS);
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().truncatedTo(ChronoUnit.DAYS);
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC);
        }
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC);
        }
        if (value instanceof LocalDate localDate) {
            return localDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        }
        String text = value.toString().trim();
        if (text.length() >= 10 && text.charAt(4) == '-') {
            return LocalDate.parse(text.substring(0, 10)).atStartOfDay().toInstant(ZoneOffset.UTC);
        }
        return Instant.parse(text).truncatedTo(ChronoUnit.DAYS);
    }

    public List<LlmRunRow> aggregateLlmRunsByToolAndModel(Instant from, Instant to, String user, String sourceTool) {
        QueryParts parts = buildFilter(from, to, user, sourceTool);
        String sql = """
                SELECT t.source_tool AS source_tool,
                       COALESCE(tu.model, 'unknown') AS model,
                       COUNT(*) AS turn_count,
                       COALESCE(SUM(tu.estimated_cost_usd), 0) AS cost_usd
                FROM token_usage tu
                JOIN tasks t ON t.id = tu.task_id
                JOIN agents a ON a.id = t.agent_id
                WHERE 1=1
                %s
                GROUP BY t.source_tool, COALESCE(tu.model, 'unknown')
                ORDER BY turn_count DESC, cost_usd DESC
                """.formatted(parts.whereClause());

        var query = entityManager.createNativeQuery(sql);
        parts.bindParams(query);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<LlmRunRow> results = new ArrayList<>();
        for (Object[] row : rows) {
            results.add(new LlmRunRow(
                    row[0] != null ? row[0].toString() : "unknown",
                    row[1] != null ? row[1].toString() : "unknown",
                    row[2] != null ? ((Number) row[2]).intValue() : 0,
                    toDouble(row[3])
            ));
        }
        return results;
    }

    public List<TaskCostRow> topCostlyTasks(Instant from, Instant to, String user, String sourceTool, int limit) {
        QueryParts parts = buildFilter(from, to, user, sourceTool);
        // Use JPA setMaxResults instead of SQL LIMIT/FETCH — portable across RDBMS vendors.
        String sql = """
                SELECT t.id, t.description, t.source_tool, t.retry_count,
                       COALESCE(SUM(tu.estimated_cost_usd), 0) AS total_cost
                FROM token_usage tu
                JOIN tasks t ON t.id = tu.task_id
                JOIN agents a ON a.id = t.agent_id
                WHERE 1=1
                %s
                GROUP BY t.id, t.description, t.source_tool, t.retry_count
                ORDER BY total_cost DESC
                """.formatted(parts.whereClause());

        var query = entityManager.createNativeQuery(sql);
        parts.bindParams(query);
        query.setMaxResults(limit);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<TaskCostRow> results = new ArrayList<>();
        for (Object[] row : rows) {
            results.add(new TaskCostRow(
                    row[0].toString(),
                    row[1] != null ? row[1].toString() : "",
                    row[2] != null ? row[2].toString() : "",
                    row[3] != null ? ((Number) row[3]).intValue() : 0,
                    toDouble(row[4])
            ));
        }
        return results;
    }

    public double sumCostForScope(Instant from, Instant to, String scope, String scopeValue) {
        StringBuilder where = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        appendDateFilters(where, params, from, to);

        if ("USER".equals(scope) && scopeValue != null && !scopeValue.isBlank()) {
            where.append(" AND LOWER(a.developer_email) = LOWER(:scopeValue)");
            params.put("scopeValue", scopeValue.trim());
        } else if ("TOOL".equals(scope) && scopeValue != null && !scopeValue.isBlank()) {
            where.append(" AND t.source_tool = :scopeValue");
            params.put("scopeValue", scopeValue.trim());
        }

        String sql = """
                SELECT COALESCE(SUM(tu.estimated_cost_usd), 0)
                FROM token_usage tu
                JOIN tasks t ON t.id = tu.task_id
                JOIN agents a ON a.id = t.agent_id
                WHERE 1=1
                %s
                """.formatted(where);

        var query = entityManager.createNativeQuery(sql);
        params.forEach(query::setParameter);
        return toDouble(query.getSingleResult());
    }

    private List<Map.Entry<String, Double>> runKeyValueQuery(String sql, QueryParts parts) {
        var query = entityManager.createNativeQuery(sql);
        parts.bindParams(query);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<Map.Entry<String, Double>> results = new ArrayList<>();
        for (Object[] row : rows) {
            results.add(Map.entry(row[0].toString(), toDouble(row[1])));
        }
        return results;
    }

    private QueryParts buildFilter(Instant from, Instant to, String user, String sourceTool) {
        StringBuilder where = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        appendDateFilters(where, params, from, to);

        if (user != null && !user.isBlank()) {
            where.append("""
                     AND (
                        LOWER(a.os_username) LIKE LOWER(:userPattern)
                        OR LOWER(a.display_name) LIKE LOWER(:userPattern)
                        OR LOWER(a.developer_email) LIKE LOWER(:userPattern)
                        OR LOWER(a.git_email) LIKE LOWER(:userPattern)
                     )
                    """);
            params.put("userPattern", "%" + user.trim() + "%");
        }
        if (sourceTool != null && !sourceTool.isBlank()) {
            where.append(" AND t.source_tool = :sourceTool");
            params.put("sourceTool", sourceTool.trim());
        }
        return new QueryParts(where.toString(), params);
    }

    private static void appendDateFilters(StringBuilder where, Map<String, Object> params, Instant from, Instant to) {
        if (from != null) {
            where.append(" AND tu.recorded_at >= :from");
            params.put("from", from);
        }
        if (to != null) {
            where.append(" AND tu.recorded_at <= :to");
            params.put("to", to);
        }
    }

    private static double toDouble(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private record QueryParts(String whereClause, Map<String, Object> params) {
        void bindParams(jakarta.persistence.Query query) {
            params.forEach(query::setParameter);
        }
    }

    public record UserCostRow(String developerEmail, String displayName, double costUsd) {
    }

    public record TaskCostRow(String taskId, String description, String sourceTool, int retryCount, double totalCostUsd) {
    }

    public record LlmRunRow(String sourceTool, String model, int turnCount, double costUsd) {
    }

    public record SeriesTrendRow(Instant day, String series, double value) {
    }
}
