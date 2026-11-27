/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.manager.config.AnalyticsProperties;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class DateRangeClamp {

    private DateRangeClamp() {
    }

    public record Range(Instant from, Instant to) {
    }

    public static Range clamp(Instant from, Instant to, AnalyticsProperties properties) {
        Instant resolvedTo = to != null ? to : Instant.now();
        Instant resolvedFrom = from != null ? from : resolvedTo.minus(7, ChronoUnit.DAYS);
        if (resolvedFrom.isAfter(resolvedTo)) {
            Instant tmp = resolvedFrom;
            resolvedFrom = resolvedTo;
            resolvedTo = tmp;
        }
        long maxDays = Math.max(1, properties.getMaxQueryRangeDays());
        if (ChronoUnit.DAYS.between(resolvedFrom, resolvedTo) > maxDays) {
            resolvedFrom = resolvedTo.minus(maxDays, ChronoUnit.DAYS);
        }
        return new Range(resolvedFrom, resolvedTo);
    }

    public static boolean preferRollups(Instant from, Instant to, AnalyticsProperties properties) {
        return ChronoUnit.DAYS.between(from, to) >= properties.getRollupPreferMinDays();
    }
}
