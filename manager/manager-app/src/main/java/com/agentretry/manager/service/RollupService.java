/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.manager.graphql.TimeSeriesPoint;
import com.agentretry.manager.graphql.TrendSeries;
import com.agentretry.manager.persistence.DatabaseSqlDialect;
import com.agentretry.manager.platform.FeatureGateService;
import com.agentretry.manager.platform.PlatformFeature;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class RollupService {

    private static final Logger log = LoggerFactory.getLogger(RollupService.class);

    private final FeatureGateService featureGateService;
    private final DatabaseSqlDialect sqlDialect;

    @PersistenceContext
    private EntityManager entityManager;

    public RollupService(FeatureGateService featureGateService, DatabaseSqlDialect sqlDialect) {
        this.featureGateService = featureGateService;
        this.sqlDialect = sqlDialect;
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void scheduledRefresh() {
        if (!featureGateService.isEnabled(PlatformFeature.ROLLUP_ANALYTICS)) {
            return;
        }
        refreshRecentDays(3);
    }

    @Transactional
    public void refreshRecentDays(int days) {
        LocalDate end = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = end.minusDays(Math.max(1, days));
        refreshRange(start, end);
    }

    @Transactional
    public void refreshRange(LocalDate start, LocalDate end) {
        Instant from = start.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        String day = sqlDialect.rollupDayColumn();
        String costDay = sqlDialect.castToDate("tu.recorded_at");
        String taskDay = sqlDialect.castToDate("t.started_at");

        entityManager.createNativeQuery(
                        "DELETE FROM daily_cost_rollups WHERE " + day + " >= :start AND " + day + " <= :end")
                .setParameter("start", start)
                .setParameter("end", end)
                .executeUpdate();
        entityManager.createNativeQuery(
                        "DELETE FROM daily_retry_rollups WHERE " + day + " >= :start AND " + day + " <= :end")
                .setParameter("start", start)
                .setParameter("end", end)
                .executeUpdate();

        entityManager.createNativeQuery("""
                INSERT INTO daily_cost_rollups (%s, source_tool, model, cost_usd, retry_cost_usd, retry_count, turn_count)
                SELECT %s AS %s,
                       t.source_tool,
                       COALESCE(tu.model, 'unknown'),
                       COALESCE(SUM(tu.estimated_cost_usd), 0),
                       0,
                       0,
                       COUNT(*)
                FROM token_usage tu
                JOIN tasks t ON t.id = tu.task_id
                WHERE tu.recorded_at >= :from AND tu.recorded_at < :to
                GROUP BY %s, t.source_tool, COALESCE(tu.model, 'unknown')
                """.formatted(day, costDay, day, costDay))
                .setParameter("from", from)
                .setParameter("to", to)
                .executeUpdate();

        entityManager.createNativeQuery("""
                INSERT INTO daily_retry_rollups (%s, source_tool, model, retry_count)
                SELECT %s AS %s,
                       t.source_tool,
                       'unknown',
                       COALESCE(SUM(t.retry_count), 0)
                FROM tasks t
                WHERE t.started_at >= :from AND t.started_at < :to
                GROUP BY %s, t.source_tool
                """.formatted(day, taskDay, day, taskDay))
                .setParameter("from", from)
                .setParameter("to", to)
                .executeUpdate();

        log.info("Refreshed rollups from {} to {}", start, end);
    }

    @Transactional(readOnly = true)
    public List<TimeSeriesPoint> costTrendTotal(Instant from, Instant to) {
        LocalDate start = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate end = to.atZone(ZoneOffset.UTC).toLocalDate();
        String day = sqlDialect.rollupDayColumn();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT %s, COALESCE(SUM(cost_usd), 0)
                FROM daily_cost_rollups
                WHERE %s >= :start AND %s <= :end
                GROUP BY %s
                ORDER BY %s
                """.formatted(day, day, day, day, day))
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
        return toPoints(from, to, rows);
    }

    @Transactional(readOnly = true)
    public List<TrendSeries> costTrendByTool(Instant from, Instant to) {
        return seriesFromCost(from, to, "source_tool");
    }

    @Transactional(readOnly = true)
    public List<TrendSeries> costTrendByModel(Instant from, Instant to) {
        return seriesFromCost(from, to, "model");
    }

    @Transactional(readOnly = true)
    public List<TimeSeriesPoint> retryTrendTotal(Instant from, Instant to) {
        LocalDate start = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate end = to.atZone(ZoneOffset.UTC).toLocalDate();
        String day = sqlDialect.rollupDayColumn();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT %s, COALESCE(SUM(retry_count), 0)
                FROM daily_retry_rollups
                WHERE %s >= :start AND %s <= :end
                GROUP BY %s
                ORDER BY %s
                """.formatted(day, day, day, day, day))
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
        return toPoints(from, to, rows);
    }

    @Transactional(readOnly = true)
    public List<TrendSeries> retryTrendByTool(Instant from, Instant to) {
        return seriesFromRetry(from, to, "source_tool");
    }

    @Transactional(readOnly = true)
    public List<TrendSeries> retryTrendByModel(Instant from, Instant to) {
        return seriesFromRetry(from, to, "model");
    }

    public boolean hasRollups(Instant from, Instant to) {
        if (!featureGateService.isEnabled(PlatformFeature.ROLLUP_ANALYTICS)) {
            return false;
        }
        LocalDate start = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate end = to.atZone(ZoneOffset.UTC).toLocalDate();
        String day = sqlDialect.rollupDayColumn();
        Object count = entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM daily_cost_rollups WHERE " + day + " >= :start AND " + day + " <= :end")
                .setParameter("start", start)
                .setParameter("end", end)
                .getSingleResult();
        return ((Number) count).longValue() > 0;
    }

    private List<TrendSeries> seriesFromCost(Instant from, Instant to, String dimension) {
        LocalDate start = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate end = to.atZone(ZoneOffset.UTC).toLocalDate();
        String day = sqlDialect.rollupDayColumn();
        String sql = """
                SELECT %s, %s AS series_name, COALESCE(SUM(cost_usd), 0)
                FROM daily_cost_rollups
                WHERE %s >= :start AND %s <= :end
                GROUP BY %s, %s
                ORDER BY %s
                """.formatted(day, dimension, day, day, day, dimension, day);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
        return toTrendSeries(from, to, rows);
    }

    private List<TrendSeries> seriesFromRetry(Instant from, Instant to, String dimension) {
        LocalDate start = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate end = to.atZone(ZoneOffset.UTC).toLocalDate();
        String day = sqlDialect.rollupDayColumn();
        String sql = """
                SELECT %s, %s AS series_name, COALESCE(SUM(retry_count), 0)
                FROM daily_retry_rollups
                WHERE %s >= :start AND %s <= :end
                GROUP BY %s, %s
                ORDER BY %s
                """.formatted(day, dimension, day, day, day, dimension, day);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
        return toTrendSeries(from, to, rows);
    }

    private List<TimeSeriesPoint> toPoints(Instant from, Instant to, List<Object[]> rows) {
        List<Map.Entry<Instant, Double>> dayTotals = new ArrayList<>();
        for (Object[] row : rows) {
            LocalDate day = LocalDate.parse(row[0].toString().substring(0, 10));
            Instant key = day.atStartOfDay().toInstant(ZoneOffset.UTC);
            dayTotals.add(Map.entry(key, toDouble(row[1])));
        }
        return TrendSeriesBuilder.fromDayTotals(from, to, dayTotals);
    }

    private List<TrendSeries> toTrendSeries(Instant from, Instant to, List<Object[]> rows) {
        Map<Instant, Map<String, Double>> daySeries = new HashMap<>();
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Object[] row : rows) {
            LocalDate day = LocalDate.parse(row[0].toString().substring(0, 10));
            Instant key = day.atStartOfDay().toInstant(ZoneOffset.UTC);
            String name = row[1] != null ? row[1].toString() : "unknown";
            names.add(name);
            daySeries.computeIfAbsent(key, ignored -> new HashMap<>()).merge(name, toDouble(row[2]), Double::sum);
        }
        List<TrendSeries> series = new ArrayList<>();
        for (String name : names) {
            List<TimeSeriesPoint> points = new ArrayList<>();
            Instant cursor = from.truncatedTo(ChronoUnit.DAYS);
            Instant end = to.truncatedTo(ChronoUnit.DAYS);
            while (!cursor.isAfter(end)) {
                double value = daySeries.getOrDefault(cursor, Map.of()).getOrDefault(name, 0.0);
                points.add(new TimeSeriesPoint(cursor.toString(), value));
                cursor = cursor.plus(1, ChronoUnit.DAYS);
            }
            series.add(new TrendSeries(name, points));
        }
        return series;
    }

    private static double toDouble(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof BigDecimal bd) {
            return bd.doubleValue();
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }
}
