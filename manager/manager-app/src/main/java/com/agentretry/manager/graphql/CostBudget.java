/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import com.agentretry.manager.domain.BudgetPeriod;
import com.agentretry.manager.domain.BudgetScope;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CostBudget(
        UUID id,
        String name,
        BudgetScope scope,
        String scopeValue,
        BudgetPeriod period,
        BigDecimal limitUsd,
        BigDecimal alertThresholdPct,
        boolean active,
        BigDecimal currentSpendUsd,
        Instant createdAt,
        Instant updatedAt
) {
}
