/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.manager.graphql.TimeSeriesPoint;
import com.agentretry.manager.graphql.TrendSeries;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds daily multi-series trends and collapses long tails into {@code Other}.
 */
final class TrendSeriesBuilder {

    private static final int DEFAULT_TOP_SERIES = 6;
    private static final String OTHER = "Other";

    private TrendSeriesBuilder() {
    }

    static List<TrendSeries> fromDaySeriesValues(
            Instant from,
            Instant to,
            Map<Instant, Map<String, Double>> dayToSeriesValues,
            int topN
    ) {
        Instant start = from.truncatedTo(ChronoUnit.DAYS);
        Instant end = to.truncatedTo(ChronoUnit.DAYS);

        Map<String, Double> totals = new HashMap<>();
        for (Map<String, Double> seriesValues : dayToSeriesValues.values()) {
            for (Map.Entry<String, Double> entry : seriesValues.entrySet()) {
                totals.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }

        List<String> ranked = totals.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .toList();

        List<String> kept = ranked.size() <= topN
                ? ranked
                : ranked.subList(0, topN);
        boolean hasOther = ranked.size() > topN;

        Map<String, Map<Instant, Double>> seriesBuckets = new LinkedHashMap<>();
        for (String name : kept) {
            seriesBuckets.put(name, emptyDayBuckets(start, end));
        }
        if (hasOther) {
            seriesBuckets.put(OTHER, emptyDayBuckets(start, end));
        }

        for (Map.Entry<Instant, Map<String, Double>> dayEntry : dayToSeriesValues.entrySet()) {
            Instant day = dayEntry.getKey().truncatedTo(ChronoUnit.DAYS);
            if (day.isBefore(start) || day.isAfter(end)) {
                continue;
            }
            for (Map.Entry<String, Double> seriesEntry : dayEntry.getValue().entrySet()) {
                String target = kept.contains(seriesEntry.getKey()) ? seriesEntry.getKey() : OTHER;
                Map<Instant, Double> buckets = seriesBuckets.get(target);
                if (buckets != null) {
                    buckets.computeIfPresent(day, (ignored, value) -> value + seriesEntry.getValue());
                }
            }
        }

        List<TrendSeries> result = new ArrayList<>();
        for (Map.Entry<String, Map<Instant, Double>> entry : seriesBuckets.entrySet()) {
            List<TimeSeriesPoint> points = new ArrayList<>();
            for (Map.Entry<Instant, Double> point : entry.getValue().entrySet()) {
                points.add(new TimeSeriesPoint(point.getKey().toString(), point.getValue()));
            }
            result.add(new TrendSeries(entry.getKey(), points));
        }
        return result;
    }

    static List<TrendSeries> fromDaySeriesValues(
            Instant from,
            Instant to,
            Map<Instant, Map<String, Double>> dayToSeriesValues
    ) {
        return fromDaySeriesValues(from, to, dayToSeriesValues, DEFAULT_TOP_SERIES);
    }

    static List<TrendSeries> fromRows(
            Instant from,
            Instant to,
            List<SeriesDayValue> rows
    ) {
        Map<Instant, Map<String, Double>> dayToSeriesValues = new HashMap<>();
        for (SeriesDayValue row : rows) {
            Instant day = row.day().truncatedTo(ChronoUnit.DAYS);
            dayToSeriesValues
                    .computeIfAbsent(day, ignored -> new HashMap<>())
                    .merge(row.series(), row.value(), Double::sum);
        }
        return fromDaySeriesValues(from, to, dayToSeriesValues);
    }

    static Map<Instant, Double> emptyDayBuckets(Instant start, Instant end) {
        Map<Instant, Double> buckets = new TreeMap<>();
        Instant cursor = start.truncatedTo(ChronoUnit.DAYS);
        Instant last = end.truncatedTo(ChronoUnit.DAYS);
        while (!cursor.isAfter(last)) {
            buckets.put(cursor, 0.0);
            cursor = cursor.plus(1, ChronoUnit.DAYS);
        }
        return buckets;
    }

    /**
     * Builds a dense daily total series for {@code [from, to]}, zero-filling missing days.
     */
    static List<TimeSeriesPoint> fromDayTotals(
            Instant from,
            Instant to,
            Iterable<? extends Map.Entry<Instant, Double>> dayTotals
    ) {
        Map<Instant, Double> buckets = emptyDayBuckets(from, to);
        for (Map.Entry<Instant, Double> entry : dayTotals) {
            if (entry.getKey() == null) {
                continue;
            }
            Instant day = entry.getKey().truncatedTo(ChronoUnit.DAYS);
            buckets.computeIfPresent(day, (ignored, value) -> value + entry.getValue());
        }
        return toPoints(buckets);
    }

    static List<TimeSeriesPoint> toPoints(Map<Instant, Double> buckets) {
        List<TimeSeriesPoint> points = new ArrayList<>();
        for (Map.Entry<Instant, Double> entry : buckets.entrySet()) {
            points.add(new TimeSeriesPoint(entry.getKey().toString(), entry.getValue()));
        }
        return points;
    }

    record SeriesDayValue(Instant day, String series, double value) {
    }
}
