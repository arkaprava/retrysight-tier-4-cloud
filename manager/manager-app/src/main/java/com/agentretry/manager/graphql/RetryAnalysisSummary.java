/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import java.util.List;

public record RetryAnalysisSummary(
        List<ModelRetryMetric> retriesByModel,
        List<ModelRetryMetric> retryCostByModel,
        List<ModelRetryMetric> retryWasteByModel,
        List<ModelRetryRateMetric> retryRateByModel,
        List<ModelRetryTypeMetric> retryTypeByModel,
        List<RetryTaskByModelSummary> topRetryTasksByModel,
        int unattributedRetryCount
) {
    public static RetryAnalysisSummary empty() {
        return new RetryAnalysisSummary(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0);
    }
}
