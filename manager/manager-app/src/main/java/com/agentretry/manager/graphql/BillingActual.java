/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import com.agentretry.manager.domain.BillingActualSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BillingActual(
        UUID id,
        Instant periodStart,
        Instant periodEnd,
        BigDecimal actualCostUsd,
        BillingActualSource source,
        String notes,
        Instant importedAt,
        String importedBy
) {
}
