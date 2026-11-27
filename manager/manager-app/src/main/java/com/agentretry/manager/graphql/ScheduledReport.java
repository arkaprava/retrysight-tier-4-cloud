/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import java.time.Instant;
import java.util.UUID;

public record ScheduledReport(
        UUID id,
        String name,
        String dataset,
        String format,
        String cronExpression,
        String emailTo,
        boolean active,
        Instant lastRunAt,
        Instant createdAt,
        Instant updatedAt
) {
}
