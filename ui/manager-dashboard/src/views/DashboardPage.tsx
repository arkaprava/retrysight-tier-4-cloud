/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useMemo } from 'react'
import { useQuery } from '@apollo/client'
import AnalyticsChart from '../components/analytics/AnalyticsChart'
import BarListPanel from '../components/analytics/BarListPanel'
import LlmRunsByIdePanel from '../components/analytics/LlmRunsByIdePanel'
import MetricCard from '../components/analytics/MetricCard'
import MetricGrid from '../components/analytics/MetricGrid'
import TrendBreakdownChart from '../components/analytics/TrendBreakdownChart'
import { DASHBOARD_SUMMARY, COST_SUMMARY, AGENTS_QUERY } from '../graphql/client'
import {
  DASHBOARD_POLL_MS,
  useStablePreviousWeekRange,
} from '../hooks/useRollingWeekRanges'
import { formatSourceTool } from '../sourceTools'
import { colorForSourceTool } from '../theme/tremor/chart-colors'
import { usePlatformFeatures } from '../platformFeatures'
import { buildMetricDelta } from '../utils/metricDelta'
import {
  avgCostPerTask,
  countActiveDevelopers,
  countActiveTools,
  countLiveAgents,
  sumLlmTurns,
  sumToolMetrics,
} from '../utils/dashboardMetrics'
import { formatUsd } from '../utils/contextUsage'

function LoadingState({ label }: { label: string }) {
  return (
    <div className="loading-state">
      <div className="loading-spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  )
}

function totalTokensFromSummary(summary: {
  totalTokens: {
    inputTokens: number
    outputTokens: number
    cacheReadTokens: number
    cacheWriteTokens: number
    reasoningTokens: number
  }
}) {
  return (
    summary.totalTokens.inputTokens +
    summary.totalTokens.outputTokens +
    summary.totalTokens.cacheReadTokens +
    summary.totalTokens.cacheWriteTokens +
    summary.totalTokens.reasoningTokens
  )
}

export default function DashboardPage() {
  const { evaluationsEnabled } = usePlatformFeatures()
  const previousRange = useStablePreviousWeekRange()

  const liveQueryOptions = {
    pollInterval: DASHBOARD_POLL_MS,
    notifyOnNetworkStatusChange: false,
    fetchPolicy: 'cache-first' as const,
  }

  const { data, loading, error } = useQuery(DASHBOARD_SUMMARY, liveQueryOptions)

  const { data: prevData } = useQuery(DASHBOARD_SUMMARY, {
    variables: previousRange,
    notifyOnNetworkStatusChange: false,
    fetchPolicy: 'cache-first',
  })

  const { data: costData } = useQuery(COST_SUMMARY, liveQueryOptions)

  const { data: prevCostData } = useQuery(COST_SUMMARY, {
    variables: previousRange,
    notifyOnNetworkStatusChange: false,
    fetchPolicy: 'cache-first',
  })

  const { data: agentsData } = useQuery(AGENTS_QUERY, liveQueryOptions)

  const retriesByTool = useMemo(
    () => (data?.dashboardSummary?.retriesByTool ?? []).map((entry: { tool: string; value: number }) => ({
      name: formatSourceTool(entry.tool),
      value: entry.value,
      color: colorForSourceTool(entry.tool),
    })),
    [data?.dashboardSummary?.retriesByTool],
  )

  const costByTool = useMemo(
    () => (costData?.costSummary?.costByTool ?? []).map((entry: { tool: string; value: number }) => ({
      tool: formatSourceTool(entry.tool),
      value: entry.value,
    })),
    [costData?.costSummary?.costByTool],
  )

  if (loading && !data) return <LoadingState label="Loading dashboard…" />
  if (error) return <p className="empty">Failed to load dashboard: {error.message}</p>
  if (!data?.dashboardSummary) return <p className="empty">No dashboard data available.</p>

  const summary = data.dashboardSummary
  const prevSummary = prevData?.dashboardSummary
  const costSummary = costData?.costSummary
  const prevCostSummary = prevCostData?.costSummary
  const totalTokens = totalTokensFromSummary(summary)
  const prevTotalTokens = prevSummary ? totalTokensFromSummary(prevSummary) : 0

  const totalRetries = sumToolMetrics(summary.retriesByTool)
  const prevTotalRetries = prevSummary ? sumToolMetrics(prevSummary.retriesByTool) : 0

  const llmTurns = sumLlmTurns(summary.llmRunsByToolAndModel)
  const prevLlmTurns = prevSummary ? sumLlmTurns(prevSummary.llmRunsByToolAndModel) : 0

  const activeDevelopers = countActiveDevelopers(costSummary?.costByUser)
  const prevActiveDevelopers = countActiveDevelopers(prevCostSummary?.costByUser)

  const activeIdes = countActiveTools(summary.retriesByTool)
  const prevActiveIdes = prevSummary ? countActiveTools(prevSummary.retriesByTool) : 0

  const totalCostUsd = costSummary?.totalCostUsd ?? summary.totalTokens.estimatedCostUsd
  const prevTotalCostUsd = prevCostSummary?.totalCostUsd
    ?? prevSummary?.totalTokens.estimatedCostUsd
    ?? 0
  const avgCost = avgCostPerTask(totalCostUsd, summary.totalTasks)
  const prevAvgCost = prevSummary
    ? avgCostPerTask(prevTotalCostUsd, prevSummary.totalTasks)
    : 0

  const collectors = countLiveAgents(agentsData?.agents)

  return (
    <div className="analytics-surface">
      <header className="page-header">
        <h2 className="page-title">Dashboard</h2>
        <p className="page-subtitle">
          Live metrics for the last 7 days · compared to the prior week
        </p>
      </header>

      <MetricGrid className="section-gap">
        <MetricCard
          title="Total Tasks"
          value={summary.totalTasks.toLocaleString()}
          delta={prevSummary
            ? buildMetricDelta(summary.totalTasks, prevSummary.totalTasks)
            : undefined}
          live
        />
        <MetricCard
          title="Avg Retries / Task"
          value={summary.avgRetriesPerTask.toFixed(1)}
          delta={prevSummary
            ? buildMetricDelta(summary.avgRetriesPerTask, prevSummary.avgRetriesPerTask, true)
            : undefined}
          live
        />
        <MetricCard
          title="Total Tokens"
          value={totalTokens.toLocaleString()}
          delta={prevSummary
            ? buildMetricDelta(totalTokens, prevTotalTokens)
            : undefined}
          live
        />
        <MetricCard
          title="Est. Cost (USD)"
          value={`$${summary.totalTokens.estimatedCostUsd.toFixed(2)}`}
          delta={prevSummary
            ? buildMetricDelta(
                summary.totalTokens.estimatedCostUsd,
                prevSummary.totalTokens.estimatedCostUsd,
                true,
              )
            : undefined}
          live
        />
        <MetricCard
          title="Cost Lost to Retries"
          value={`$${summary.retryTokenCostUsd.toFixed(2)}`}
          delta={prevSummary
            ? buildMetricDelta(summary.retryTokenCostUsd, prevSummary.retryTokenCostUsd, true)
            : undefined}
          variant="alert"
          live
        />
        {costSummary && (
          <MetricCard
            title="Retry Waste %"
            value={`${(costSummary.retryWasteRatio * 100).toFixed(1)}%`}
            delta={prevCostSummary
              ? buildMetricDelta(
                  costSummary.retryWasteRatio,
                  prevCostSummary.retryWasteRatio,
                  true,
                )
              : undefined}
            variant="alert"
            live
          />
        )}
        {evaluationsEnabled && summary.evalSummary && summary.evalSummary.totalRuns > 0 && (
          <MetricCard
            title="Eval Pass Rate"
            value={`${(summary.evalSummary.passRate * 100).toFixed(0)}%`}
            subtitle={`${summary.evalSummary.passCount}/${summary.evalSummary.totalRuns} passed`}
            delta={prevSummary?.evalSummary && prevSummary.evalSummary.totalRuns > 0
              ? buildMetricDelta(
                  summary.evalSummary.passRate,
                  prevSummary.evalSummary.passRate,
                )
              : undefined}
            variant={summary.evalSummary.passRate >= 0.8 ? 'default' : 'alert'}
            live
          />
        )}
        <MetricCard
          title="Total Retries"
          value={totalRetries.toLocaleString()}
          delta={prevSummary
            ? buildMetricDelta(totalRetries, prevTotalRetries, true)
            : undefined}
          live
        />
        <MetricCard
          title="LLM Turns"
          value={llmTurns.toLocaleString()}
          delta={prevSummary
            ? buildMetricDelta(llmTurns, prevLlmTurns)
            : undefined}
          live
        />
        <MetricCard
          title="Active Developers"
          value={activeDevelopers.toLocaleString()}
          delta={prevCostSummary
            ? buildMetricDelta(activeDevelopers, prevActiveDevelopers)
            : undefined}
          live
        />
        <MetricCard
          title="IDEs in Use"
          value={activeIdes.toLocaleString()}
          delta={prevSummary
            ? buildMetricDelta(activeIdes, prevActiveIdes)
            : undefined}
          live
        />
        <MetricCard
          title="Avg Cost / Task"
          value={formatUsd(avgCost) ?? '$0.00'}
          delta={prevSummary
            ? buildMetricDelta(avgCost, prevAvgCost, true)
            : undefined}
          live
        />
        <MetricCard
          title="Active Collectors"
          value={collectors.active.toLocaleString()}
          subtitle={collectors.total > 0
            ? `${collectors.stale} stale · ${collectors.total} registered`
            : 'No collectors registered'}
          live
        />
      </MetricGrid>

      <div className="analytics-chart-grid section-gap">
        <TrendBreakdownChart
          title="Cost Trend"
          total={costSummary?.costTrend ?? []}
          byTool={costSummary?.costTrendByTool ?? []}
          byModel={costSummary?.costTrendByModel ?? []}
          valueFormatter={(value) => formatUsd(value) ?? '$0.00'}
        />
        <TrendBreakdownChart
          title="Retry Trend"
          total={summary.retryTrend}
          byTool={summary.retryTrendByTool ?? []}
          byModel={summary.retryTrendByModel ?? []}
        />
      </div>

      <div className="analytics-chart-grid section-gap">
        <AnalyticsChart
          title="Cost by Tool"
          type="bar"
          data={costByTool}
          index="tool"
          categories={['value']}
          valueFormatter={(value) => formatUsd(value) ?? '$0.00'}
          align="start"
          colorForIndex={colorForSourceTool}
        />
        <BarListPanel
          title="Retries by Tool"
          data={retriesByTool}
        />
      </div>

      <LlmRunsByIdePanel
        runs={data?.dashboardSummary?.llmRunsByToolAndModel ?? []}
        showCost
        className="section-gap"
      />
    </div>
  )
}
