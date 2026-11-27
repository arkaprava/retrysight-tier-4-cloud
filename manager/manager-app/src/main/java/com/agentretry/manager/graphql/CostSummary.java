/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

public record CostSummary(
        double totalCostUsd,
        double retryCostUsd,
        double retryWasteRatio,
        java.util.List<ToolMetric> costByTool,
        java.util.List<ToolMetric> costByModel,
        java.util.List<LlmRunMetric> llmRunsByToolAndModel,
        java.util.List<UserCostMetric> costByUser,
        java.util.List<TimeSeriesPoint> costTrend,
        java.util.List<TrendSeries> costTrendByTool,
        java.util.List<TrendSeries> costTrendByModel,
        java.util.List<TimeSeriesPoint> retryCostTrend,
        java.util.List<TrendSeries> retryCostTrendByTool,
        java.util.List<TrendSeries> retryCostTrendByModel,
        java.util.List<CostlyTaskSummary> topCostlyTasks
) {
    public static CostSummary disabled() {
        return new CostSummary(
                0, 0, 0,
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of()
        );
    }
}
