/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import java.time.Instant;
import java.util.UUID;

public record EvalScorecard(
        UUID id,
        UUID taskId,
        double score,
        String notes,
        String createdBy,
        Instant createdAt
) {
}
