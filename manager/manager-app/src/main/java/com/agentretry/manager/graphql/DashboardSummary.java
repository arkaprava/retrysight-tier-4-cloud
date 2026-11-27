/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import com.agentretry.manager.domain.TaskEntity;

import java.util.List;

public record DashboardSummary(
        int totalTasks,
        double avgRetriesPerTask,
        TokenUsageSummary totalTokens,
        double retryTokenCostUsd,
        List<ToolMetric> retriesByTool,
        List<ToolMetric> tokensByTool,
        List<LlmRunMetric> llmRunsByToolAndModel,
        List<TimeSeriesPoint> retryTrend,
        List<TrendSeries> retryTrendByTool,
        List<TrendSeries> retryTrendByModel,
        List<TaskEntity> topRetryTasks,
        EvalSummary evalSummary,
        List<TimeSeriesPoint> evalPassRateTrend
) {
}
