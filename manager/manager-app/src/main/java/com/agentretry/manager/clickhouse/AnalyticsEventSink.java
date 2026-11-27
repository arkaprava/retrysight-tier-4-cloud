/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.clickhouse;

/**
 * Optional analytics warehouse sink. RDBMS remains system of record; this is dual-write only.
 */
public interface AnalyticsEventSink {

    boolean isEnabled();

    void publishTokenUsage(TokenUsageAnalyticsRow row);

    void publishTaskEvent(TaskEventAnalyticsRow row);

    /** Best-effort flush of buffered rows (used on shutdown / tests). */
    void flush();
}
