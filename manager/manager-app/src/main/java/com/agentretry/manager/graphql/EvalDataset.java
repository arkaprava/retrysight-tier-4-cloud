/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import com.agentretry.manager.eval.EvalDatasetSource;

import java.time.Instant;
import java.util.UUID;

public record EvalDataset(
        UUID id,
        String name,
        String description,
        EvalDatasetSource source,
        String filterJson,
        int taskCount,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
