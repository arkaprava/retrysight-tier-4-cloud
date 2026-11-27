/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.clickhouse;

import com.agentretry.manager.config.AnalyticsProperties;
import com.agentretry.manager.repository.CostAnalyticsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cost/analytics reads from ClickHouse. Falls back to empty Optional on errors
 * so callers can use the RDBMS repository.
 */
@Service
@ConditionalOnProperty(prefix = "manager.analytics.clickhouse", name = "enabled", havingValue = "true")
public class ClickHouseCostQueryService {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseCostQueryService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC);

    private final AnalyticsProperties.ClickHouse props;
    private final ClickHouseHttpClient client;
    private final ObjectMapper objectMapper;

    public ClickHouseCostQueryService(
            AnalyticsProperties analyticsProperties,
            ClickHouseHttpClient client,
            ObjectMapper objectMapper
    ) {
        this.props = analyticsProperties.getClickhouse();
        this.client = client;
        this.objectMapper = objectMapper;
    }

    public boolean isReadEnabled() {
        return props.isEnabled() && props.isReadEnabled();
    }

    public Optional<Double> sumTotalCost(Instant from, Instant to, String user, String sourceTool) {
        String sql = """
                SELECT coalesce(sum(estimated_cost_usd), 0) AS metric_value
                FROM token_usage_analytics
                WHERE recorded_at >= toDateTime64('%s', 3, 'UTC')
                  AND recorded_at < toDateTime64('%s', 3, 'UTC')
                  %s
                """.formatted(TS.format(from), TS.format(to), filters(user, sourceTool));
        return querySingleDouble(sql);
    }

    public Optional<List<Map.Entry<String, Double>>> aggregateCostByTool(
            Instant from, Instant to, String user, String sourceTool
    ) {
        String sql = """
                SELECT source_tool AS metric_key, coalesce(sum(estimated_cost_usd), 0) AS metric_value
                FROM token_usage_analytics
                WHERE recorded_at >= toDateTime64('%s', 3, 'UTC')
                  AND recorded_at < toDateTime64('%s', 3, 'UTC')
                  %s
                GROUP BY source_tool
                ORDER BY metric_value DESC
                """.formatted(TS.format(from), TS.format(to), filters(user, sourceTool));
        return queryKeyValues(sql);
    }

    public Optional<List<Map.Entry<String, Double>>> aggregateCostByModel(
            Instant from, Instant to, String user, String sourceTool
    ) {
        String sql = """
                SELECT if(model = '', 'unknown', model) AS metric_key,
                       coalesce(sum(estimated_cost_usd), 0) AS metric_value
                FROM token_usage_analytics
                WHERE recorded_at >= toDateTime64('%s', 3, 'UTC')
                  AND recorded_at < toDateTime64('%s', 3, 'UTC')
                  %s
                GROUP BY metric_key
                ORDER BY metric_value DESC
                """.formatted(TS.format(from), TS.format(to), filters(user, sourceTool));
        return queryKeyValues(sql);
    }

    public Optional<List<CostAnalyticsRepository.UserCostRow>> aggregateCostByUser(
            Instant from, Instant to, String user, String sourceTool
    ) {
        String sql = """
                SELECT developer_email AS email,
                       any(developer_email) AS display_name,
                       coalesce(sum(estimated_cost_usd), 0) AS metric_value
                FROM token_usage_analytics
                WHERE recorded_at >= toDateTime64('%s', 3, 'UTC')
                  AND recorded_at < toDateTime64('%s', 3, 'UTC')
                  %s
                GROUP BY developer_email
                ORDER BY metric_value DESC
                """.formatted(TS.format(from), TS.format(to), filters(user, sourceTool));
        try {
            JsonNode data = parseData(client.queryJson(sql));
            List<CostAnalyticsRepository.UserCostRow> rows = new ArrayList<>();
            for (JsonNode row : data) {
                rows.add(new CostAnalyticsRepository.UserCostRow(
                        text(row, "email", "unknown"),
                        text(row, "display_name", null),
                        row.path("metric_value").asDouble(0)
                ));
            }
            return Optional.of(rows);
        } catch (Exception ex) {
            log.warn("ClickHouse aggregateCostByUser failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<List<Map.Entry<Instant, Double>>> aggregateCostTrend(
            Instant from, Instant to, String user, String sourceTool
    ) {
        String sql = """
                SELECT toDate(recorded_at) AS trend_day,
                       coalesce(sum(estimated_cost_usd), 0) AS metric_value
                FROM token_usage_analytics
                WHERE recorded_at >= toDateTime64('%s', 3, 'UTC')
                  AND recorded_at < toDateTime64('%s', 3, 'UTC')
                  %s
                GROUP BY trend_day
                ORDER BY trend_day ASC
                """.formatted(TS.format(from), TS.format(to), filters(user, sourceTool));
        try {
            JsonNode data = parseData(client.queryJson(sql));
            List<Map.Entry<Instant, Double>> rows = new ArrayList<>();
            for (JsonNode row : data) {
                LocalDate day = LocalDate.parse(row.path("trend_day").asText().substring(0, 10));
                rows.add(Map.entry(
                        day.atStartOfDay().toInstant(ZoneOffset.UTC),
                        row.path("metric_value").asDouble(0)
                ));
            }
            return Optional.of(rows);
        } catch (Exception ex) {
            log.warn("ClickHouse aggregateCostTrend failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<List<CostAnalyticsRepository.SeriesTrendRow>> aggregateCostTrendByTool(
            Instant from, Instant to, String user, String sourceTool
    ) {
        return seriesTrend(from, to, user, sourceTool, "source_tool");
    }

    public Optional<List<CostAnalyticsRepository.SeriesTrendRow>> aggregateCostTrendByModel(
            Instant from, Instant to, String user, String sourceTool
    ) {
        return seriesTrend(from, to, user, sourceTool, "if(model = '', 'unknown', model)");
    }

    public Optional<List<CostAnalyticsRepository.LlmRunRow>> aggregateLlmRunsByToolAndModel(
            Instant from, Instant to, String user, String sourceTool
    ) {
        String sql = """
                SELECT source_tool,
                       if(model = '', 'unknown', model) AS model,
                       count() AS turn_count,
                       coalesce(sum(estimated_cost_usd), 0) AS cost_usd
                FROM token_usage_analytics
                WHERE recorded_at >= toDateTime64('%s', 3, 'UTC')
                  AND recorded_at < toDateTime64('%s', 3, 'UTC')
                  %s
                GROUP BY source_tool, model
                ORDER BY turn_count DESC, cost_usd DESC
                """.formatted(TS.format(from), TS.format(to), filters(user, sourceTool));
        try {
            JsonNode data = parseData(client.queryJson(sql));
            List<CostAnalyticsRepository.LlmRunRow> rows = new ArrayList<>();
            for (JsonNode row : data) {
                rows.add(new CostAnalyticsRepository.LlmRunRow(
                        text(row, "source_tool", "unknown"),
                        text(row, "model", "unknown"),
                        row.path("turn_count").asInt(0),
                        row.path("cost_usd").asDouble(0)
                ));
            }
            return Optional.of(rows);
        } catch (Exception ex) {
            log.warn("ClickHouse aggregateLlmRuns failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<List<CostAnalyticsRepository.SeriesTrendRow>> seriesTrend(
            Instant from, Instant to, String user, String sourceTool, String seriesExpr
    ) {
        String sql = """
                SELECT toDate(recorded_at) AS trend_day,
                       %s AS series_name,
                       coalesce(sum(estimated_cost_usd), 0) AS metric_value
                FROM token_usage_analytics
                WHERE recorded_at >= toDateTime64('%s', 3, 'UTC')
                  AND recorded_at < toDateTime64('%s', 3, 'UTC')
                  %s
                GROUP BY trend_day, series_name
                ORDER BY trend_day ASC, series_name ASC
                """.formatted(seriesExpr, TS.format(from), TS.format(to), filters(user, sourceTool));
        try {
            JsonNode data = parseData(client.queryJson(sql));
            List<CostAnalyticsRepository.SeriesTrendRow> rows = new ArrayList<>();
            for (JsonNode row : data) {
                LocalDate day = LocalDate.parse(row.path("trend_day").asText().substring(0, 10));
                rows.add(new CostAnalyticsRepository.SeriesTrendRow(
                        day.atStartOfDay().toInstant(ZoneOffset.UTC),
                        text(row, "series_name", "unknown"),
                        row.path("metric_value").asDouble(0)
                ));
            }
            return Optional.of(rows);
        } catch (Exception ex) {
            log.warn("ClickHouse seriesTrend failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Double> querySingleDouble(String sql) {
        try {
            JsonNode data = parseData(client.queryJson(sql));
            if (!data.isArray() || data.isEmpty()) {
                return Optional.of(0.0);
            }
            return Optional.of(data.get(0).path("metric_value").asDouble(0));
        } catch (Exception ex) {
            log.warn("ClickHouse sumTotalCost failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<List<Map.Entry<String, Double>>> queryKeyValues(String sql) {
        try {
            JsonNode data = parseData(client.queryJson(sql));
            List<Map.Entry<String, Double>> rows = new ArrayList<>();
            for (JsonNode row : data) {
                rows.add(Map.entry(
                        text(row, "metric_key", "unknown"),
                        row.path("metric_value").asDouble(0)
                ));
            }
            return Optional.of(rows);
        } catch (Exception ex) {
            log.warn("ClickHouse key/value query failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private JsonNode parseData(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode data = root.path("data");
        return data.isArray() ? data : objectMapper.createArrayNode();
    }

    private static String filters(String user, String sourceTool) {
        StringBuilder sb = new StringBuilder();
        if (user != null && !user.isBlank()) {
            String pattern = escape(user.trim().toLowerCase());
            sb.append(" AND (positionCaseInsensitive(developer_email, '").append(pattern).append("') > 0)");
        }
        if (sourceTool != null && !sourceTool.isBlank()) {
            sb.append(" AND source_tool = '").append(escape(sourceTool.trim())).append("'");
        }
        return sb.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static String text(JsonNode row, String field, String defaultValue) {
        JsonNode node = row.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return defaultValue;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
