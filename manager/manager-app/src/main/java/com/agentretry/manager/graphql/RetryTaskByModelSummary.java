/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import java.util.UUID;

public record RetryTaskByModelSummary(
        String model,
        UUID taskId,
        String description,
        String sourceTool,
        int retryCount,
        double retryCostUsd
) {
}
