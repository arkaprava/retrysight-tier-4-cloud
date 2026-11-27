/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

public record EventTokenUsage(
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens,
        long reasoningTokens,
        Double estimatedCostUsd,
        String model
) {
    public EventTokenUsage(
            long inputTokens,
            long outputTokens,
            long cacheReadTokens,
            long cacheWriteTokens,
            long reasoningTokens
    ) {
        this(inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens, reasoningTokens, null, null);
    }

    public EventTokenUsage(
            long inputTokens,
            long outputTokens,
            long cacheReadTokens,
            long cacheWriteTokens,
            long reasoningTokens,
            Double estimatedCostUsd
    ) {
        this(inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens, reasoningTokens, estimatedCostUsd, null);
    }

    public long totalTokens() {
        return inputTokens + outputTokens + cacheReadTokens + cacheWriteTokens + reasoningTokens;
    }
}
