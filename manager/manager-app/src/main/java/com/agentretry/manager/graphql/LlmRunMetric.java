/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

public record LlmRunMetric(
        String sourceTool,
        String model,
        int turnCount,
        double costUsd
) {
}
