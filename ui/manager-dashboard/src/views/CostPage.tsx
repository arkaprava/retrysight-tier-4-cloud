/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useMemo, useState } from 'react'
import { useQuery } from '@apollo/client'
import { Link } from 'react-router-dom'
import AnalyticsChart from '../components/analytics/AnalyticsChart'
import BarListPanel from '../components/analytics/BarListPanel'
import LlmRunsByIdePanel from '../components/analytics/LlmRunsByIdePanel'
import MetricCard from '../components/analytics/MetricCard'
import MetricGrid from '../components/analytics/MetricGrid'
import TrendBreakdownChart from '../components/analytics/TrendBreakdownChart'
import BillingReconciliationSection from '../components/BillingReconciliationSection'
import CostBudgetsSection from '../components/CostBudgetsSection'
import { DateRangeFilter, useDefaultDateRange } from '../components/DateRangeFilter'
import { TaskUserFilter } from '../components/TaskUserFilter'
import { useAuth } from '../auth/AuthContext'
import { COST_ALERTS, COST_SUMMARY } from '../graphql/client'
import { toInstantEnd, toInstantStart } from '../reportApi'
import { formatSourceTool } from '../sourceTools'
import { colorForModel, colorForSourceTool } from '../theme/tremor/chart-colors'
import { formatUsd } from '../utils/contextUsage'

function LoadingState({ label }: { label: string }) {
  return (
    <div className="loading-state">
      <div className="loading-spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  )
}

export default function CostPage() {
  const { isSecadmin } = useAuth()
  const [{ fromDate, toDate }, setDateRange] = useDefaultDateRange()
  const [userFilter, setUserFilter] = useState('')
  const trimmedUser = userFilter.trim()

  const { data, loading, error } = useQuery(COST_SUMMARY, {
    variables: {
      from: fromDate ? toInstantStart(fromDate) : undefined,
      to: toDate ? toInstantEnd(toDate) : undefined,
      user: trimmedUser || undefined,
    },
    pollInterval: 15000,
  })

  const { data: alertsData } = useQuery(COST_ALERTS, { pollInterval: 30000 })
  const alerts = alertsData?.costAlerts ?? []

  const costByToolList = useMemo(
    () => (data?.costSummary?.costByTool ?? []).map((entry: { tool: string; value: number }) => ({
      name: formatSourceTool(entry.tool),
      value: entry.value,
      color: colorForSourceTool(entry.tool),
    })),
    [data?.costSummary?.costByTool],
  )

  if (loading) return <LoadingState label="Loading cost analytics…" />
  if (error) return <p className="empty">Failed to load cost data: {error.message}</p>

  const summary = data.costSummary

  return (
    <div className="analytics-surface">
      <header className="page-header">
        <h2 className="page-title">Cost Analytics</h2>
        <p className="page-subtitle">LLM token spend, retry waste, budgets, and billing reconciliation</p>
      </header>

      <div className="card cost-toolbar">
        <DateRangeFilter
          fromDate={fromDate}
          toDate={toDate}
          onFromChange={(value) => setDateRange((current) => ({ ...current, fromDate: value }))}
          onToChange={(value) => setDateRange((current) => ({ ...current, toDate: value }))}
        />
        <TaskUserFilter value={userFilter} onChange={setUserFilter} />
      </div>

      {alerts.length > 0 && (
        <div className="cost-alerts-banner">
          {alerts.map((alert: { id: string; budgetName: string; message: string; spendUsd: number; limitUsd: number }) => (
            <div key={alert.id} className="cost-alert-item">
              <strong>{alert.budgetName}</strong>
              <span>{alert.message}</span>
              <span className="cost-alert-spend">
                ${alert.spendUsd.toFixed(2)} / ${alert.limitUsd.toFixed(2)}
              </span>
            </div>
          ))}
        </div>
      )}

      <MetricGrid className="section-gap">
        <MetricCard title="Total Cost" value={formatUsd(summary.totalCostUsd)} />
        <MetricCard title="Retry Cost" value={formatUsd(summary.retryCostUsd)} variant="alert" />
        <MetricCard title="Retry Waste" value={`${(summary.retryWasteRatio * 100).toFixed(1)}%`} variant="alert" />
        <MetricCard
          title="Top Task Cost"
          value={
            summary.topCostlyTasks.length > 0
              ? formatUsd(summary.topCostlyTasks[0].totalCostUsd)
              : '$0.00'
          }
        />
      </MetricGrid>

      <div className="analytics-chart-grid section-gap">
        <TrendBreakdownChart
          title="Cost Trend"
          total={summary.costTrend}
          byTool={summary.costTrendByTool ?? []}
          byModel={summary.costTrendByModel ?? []}
          valueFormatter={(value) => formatUsd(value) ?? '$0.00'}
        />
        <TrendBreakdownChart
          title="Retry Cost Trend"
          total={summary.retryCostTrend}
          byTool={summary.retryCostTrendByTool ?? []}
          byModel={summary.retryCostTrendByModel ?? []}
          valueFormatter={(value) => formatUsd(value) ?? '$0.00'}
        />
      </div>

      <div className="analytics-chart-grid section-gap">
        <BarListPanel
          title="Cost by Tool"
          data={costByToolList}
          valueFormatter={(value) => formatUsd(value) ?? '$0.00'}
        />
        <AnalyticsChart
          title="Cost by Model"
          type="bar"
          data={summary.costByModel}
          index="tool"
          categories={['value']}
          valueFormatter={(value) => formatUsd(value) ?? '$0.00'}
          align="start"
          colorForIndex={colorForModel}
        />
      </div>

      <LlmRunsByIdePanel
        title="LLM spend by IDE and model"
        runs={summary.llmRunsByToolAndModel ?? []}
        showCost
        className="section-gap"
      />

      <div className="card section-gap">
        <h3>Cost by User</h3>
        {summary.costByUser.length === 0 ? (
          <p className="empty">No user cost data for this range.</p>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Developer</th>
                  <th>Display Name</th>
                  <th>Cost (USD)</th>
                </tr>
              </thead>
              <tbody>
                {summary.costByUser.map((user: { developerEmail: string; displayName?: string | null; costUsd: number }) => (
                  <tr key={user.developerEmail}>
                    <td>{user.developerEmail}</td>
                    <td>{user.displayName ?? '—'}</td>
                    <td>{formatUsd(user.costUsd)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="card section-gap">
        <h3>Top Costly Tasks</h3>
        {summary.topCostlyTasks.length === 0 ? (
          <p className="empty">No task cost data for this range.</p>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Task</th>
                  <th>Tool</th>
                  <th>Total Cost</th>
                  <th>Retry Cost</th>
                  <th>Retries</th>
                </tr>
              </thead>
              <tbody>
                {summary.topCostlyTasks.map((task: {
                  taskId: string
                  description: string
                  sourceTool: string
                  totalCostUsd: number
                  retryCostUsd: number
                  retryCount: number
                }) => (
                  <tr key={task.taskId}>
                    <td>
                      <Link to={`/tasks/${task.taskId}`}>{task.description}</Link>
                    </td>
                    <td>{formatSourceTool(task.sourceTool)}</td>
                    <td>{formatUsd(task.totalCostUsd)}</td>
                    <td>{formatUsd(task.retryCostUsd)}</td>
                    <td>{task.retryCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {isSecadmin && (
        <div className="section-gap">
          <CostBudgetsSection />
          <BillingReconciliationSection fromDate={fromDate} toDate={toDate} />
        </div>
      )}
    </div>
  )
}
