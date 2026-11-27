/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TopicPattern(
        UUID id,
        String label,
        int taskCount,
        double failRate,
        List<UUID> sampleTaskIds,
        Instant periodStart,
        Instant periodEnd,
        Instant computedAt,
        double relevanceScore,
        boolean suspicious,
        String flagReason,
        int windowDays
) {
}
