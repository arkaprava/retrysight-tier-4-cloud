/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import java.util.List;

public record BillingReconciliation(
        double estimatedCostUsd,
        double actualCostUsd,
        double varianceUsd,
        double variancePct,
        List<BillingActual> actuals
) {
}
