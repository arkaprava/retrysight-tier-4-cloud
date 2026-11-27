/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

public record ModelRetryRateMetric(String model, double retryRate, int totalTurns) {
}
