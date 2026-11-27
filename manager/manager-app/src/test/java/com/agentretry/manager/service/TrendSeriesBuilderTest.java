/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.manager.graphql.TimeSeriesPoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrendSeriesBuilderTest {

    @Test
    void fromDayTotalsZeroFillsMissingDaysInRange() {
        Instant from = Instant.parse("2026-07-10T12:00:00Z");
        Instant to = Instant.parse("2026-07-13T18:00:00Z");
        Instant activeDay = Instant.parse("2026-07-12T00:00:00Z");

        List<TimeSeriesPoint> points = TrendSeriesBuilder.fromDayTotals(
                from,
                to,
                List.of(Map.entry(activeDay, 4.5))
        );

        assertEquals(4, points.size());
        assertEquals(Instant.parse("2026-07-10T00:00:00Z").toString(), points.get(0).timestamp());
        assertEquals(0.0, points.get(0).value(), 0.0001);
        assertEquals(0.0, points.get(1).value(), 0.0001);
        assertEquals(4.5, points.get(2).value(), 0.0001);
        assertEquals(0.0, points.get(3).value(), 0.0001);
        assertEquals(activeDay.truncatedTo(ChronoUnit.DAYS).toString(), points.get(2).timestamp());
    }
}
