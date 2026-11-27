/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

public record EvalSummary(
        int totalRuns,
        int passCount,
        int failCount,
        int warnCount,
        double passRate
) {
}
