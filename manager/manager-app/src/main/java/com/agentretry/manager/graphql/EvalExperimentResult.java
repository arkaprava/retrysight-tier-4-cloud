/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import java.time.Instant;
import java.util.UUID;

public record EvalExperimentResult(
        UUID id,
        UUID experimentId,
        UUID taskId,
        String variant,
        UUID evalRunId,
        Double score,
        String label,
        Instant createdAt
) {
}
