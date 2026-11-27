/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.clickhouse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TokenUsageAnalyticsRow(
        UUID id,
        UUID taskId,
        UUID agentId,
        String developerEmail,
        String sourceTool,
        int turnIndex,
        String model,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens,
        long reasoningTokens,
        BigDecimal estimatedCostUsd,
        Instant recordedAt,
        String idempotencyKey
) {
}
