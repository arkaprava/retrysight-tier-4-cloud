/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CostAlert(
        UUID id,
        UUID budgetId,
        String budgetName,
        Instant triggeredAt,
        BigDecimal spendUsd,
        BigDecimal limitUsd,
        String message
) {
}
