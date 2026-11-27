/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.clickhouse;

import java.time.Instant;
import java.util.UUID;

public record TaskEventAnalyticsRow(
        UUID id,
        UUID taskId,
        UUID agentId,
        String developerEmail,
        String sourceTool,
        String eventType,
        boolean retry,
        int sequenceNum,
        Instant occurredAt,
        String idempotencyKey
) {
}
