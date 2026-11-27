/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import com.agentretry.manager.eval.EvalExperimentStatus;

import java.time.Instant;
import java.util.UUID;

public record EvalExperiment(
        UUID id,
        String name,
        UUID datasetId,
        UUID baselineDefinitionId,
        UUID variantDefinitionId,
        EvalExperimentStatus status,
        String notes,
        String createdBy,
        Instant createdAt,
        Instant completedAt,
        EvalExperimentComparison comparison
) {
}
