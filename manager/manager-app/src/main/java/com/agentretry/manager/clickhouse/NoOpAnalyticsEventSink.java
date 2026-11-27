/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.clickhouse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "manager.analytics.clickhouse", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpAnalyticsEventSink implements AnalyticsEventSink {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void publishTokenUsage(TokenUsageAnalyticsRow row) {
        // no-op
    }

    @Override
    public void publishTaskEvent(TaskEventAnalyticsRow row) {
        // no-op
    }

    @Override
    public void flush() {
        // no-op
    }
}
