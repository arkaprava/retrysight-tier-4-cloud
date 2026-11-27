/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useMemo, useState } from 'react'
import { useQuery } from '@apollo/client'
import { Link } from 'react-router-dom'
import AnalyticsChart from '../components/analytics/AnalyticsChart'
import BarListPanel from '../components/analytics/BarListPanel'
import MetricCard from '../components/analytics/MetricCard'
import MetricGrid from '../components/analytics/MetricGrid'
import { DateRangeFilter, useDefaultDateRange } from '../components/DateRangeFilter'
import { TaskUserFilter } from '../components/TaskUserFilter'
import { RETRY_ANALYSIS } from '../graphql/client'
import { toInstantEnd, toInstantStart } from '../reportApi'
import { formatSourceTool } from '../sourceTools'
import { colorForModel } from '../theme/tremor/chart-colors'
import { formatUsd } from '../utils/contextUsage'

type ModelMetric = { model: string; value: number }
type RateMetric = { model: string; retryRate: number; totalTurns: number }
type TypeMetric = { model: string; eventType: string; count: number }
type TopTask = {
  model: string
  taskId: string
  description: string
  sourceTool: string
  retryCount: number
  retryCostUsd: number
}

function LoadingState({ label }: { label: string }) {
  return (
    <div className="loading-state">
      <div className="loading-spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  )
}

function formatEventType(eventType: string): string {
  return eventType.replace(/_/g, ' ')
}

export default function RetriesPage() {
  const [{ fromDate, toDate }, setDateRange] = useDefaultDateRange()
  const [userFilter, setUserFilter] = useState('')
  const trimmedUser = userFilter.trim()

  const { data, loading, error } = useQuery(RETRY_ANALYSIS, {
    variables: {
      from: fromDate ? toInstantStart(fromDate) : undefined,
      to: toDate ? toInstantEnd(toDate) : undefined,
      user: trimmedUser || undefined,
    },
    pollInterval: 15000,
  })

  const retriesByModel = useMemo(
    () => (data?.retryAnalysis?.retriesByModel ?? []).map((entry: ModelMetric) => ({
      model: entry.model,
      value: entry.value,
    })),
    [data?.retryAnalysis?.retriesByModel],
  )

  const retryCostByModel = useMemo(
    () => (data?.retryAnalysis?.retryCostByModel ?? []).map((entry: ModelMetric) => ({
      model: entry.model,
      value: entry.value,
    })),
    [data?.retryAnalysis?.retryCostByModel],
  )

  const retriesByModelList = useMemo(
    () => retriesByModel.map((entry: ModelMetric) => ({
      name: entry.model,
      value: entry.value,
      color: colorForModel(entry.model),
    })),
    [retriesByModel],
  )

  if (loading) return <LoadingState label="Loading retry analysis…" />
  if (error) return <p className="empty">Failed to load retry analysis: {error.message}</p>

  const summary = data.retryAnalysis
  const totalRetries = retriesByModel.reduce((sum: number, entry: ModelMetric) => sum + entry.value, 0)
  const totalRetryCost = retryCostByModel.reduce((sum: number, entry: ModelMetric) => sum + entry.value, 0)
  const topWaste = summary.retryWasteByModel[0] as ModelMetric | undefined

  return (
    <div className="analytics-surface">
      <header className="page-header">
        <h2 className="page-title">Retry Analysis</h2>
        <p className="page-subtitle">Retry patterns and waste by underlying LLM model</p>
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

      <MetricGrid className="section-gap">
        <MetricCard title="Total Retries" value={totalRetries.toLocaleString()} />
        <MetricCard title="Retry Cost" value={formatUsd(totalRetryCost)} />
        <MetricCard title="Highest Waste Model" value={topWaste ? topWaste.model : '—'} />
        <MetricCard title="Unattributed" value={summary.unattributedRetryCount.toLocaleString()} />
      </MetricGrid>

      <div className="analytics-chart-grid section-gap">
        <AnalyticsChart
          title="Retries by Model"
          type="bar"
          data={retriesByModel}
          index="model"
          categories={['value']}
          align="start"
          colorForIndex={colorForModel}
        />
        <BarListPanel
          title="Retry Rank by Model"
          data={retriesByModelList}
        />
      </div>

      <div className="section-gap">
        <AnalyticsChart
          title="Retry Cost by Model"
          type="bar"
          data={retryCostByModel}
          index="model"
          categories={['value']}
          valueFormatter={(value) => formatUsd(value) ?? '$0.00'}
          align="start"
          colorForIndex={colorForModel}
        />
      </div>

      <div className="card section-gap">
        <h3>Model Breakdown</h3>
        {summary.retryRateByModel.length === 0 ? (
          <p className="empty">No retry data for this range.</p>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Model</th>
                  <th>Retries</th>
                  <th>Retry Cost</th>
                  <th>Waste %</th>
                  <th>Retry Rate</th>
                  <th>Turns</th>
                </tr>
              </thead>
              <tbody>
                {summary.retryRateByModel.map((rate: RateMetric) => {
                  const retries = summary.retriesByModel.find((entry: ModelMetric) => entry.model === rate.model)
                  const retryCost = summary.retryCostByModel.find((entry: ModelMetric) => entry.model === rate.model)
                  const waste = summary.retryWasteByModel.find((entry: ModelMetric) => entry.model === rate.model)
                  return (
                    <tr key={rate.model}>
                      <td>{rate.model}</td>
                      <td>{retries ? retries.value.toLocaleString() : '0'}</td>
                      <td>{formatUsd(retryCost?.value ?? 0)}</td>
                      <td>{waste ? `${(waste.value * 100).toFixed(1)}%` : '—'}</td>
                      <td>{(rate.retryRate * 100).toFixed(1)}%</td>
                      <td>{rate.totalTurns.toLocaleString()}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {summary.retryTypeByModel.length > 0 && (
        <div className="card section-gap">
          <h3>Retry Types by Model</h3>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Model</th>
                  <th>Event Type</th>
                  <th>Count</th>
                </tr>
              </thead>
              <tbody>
                {summary.retryTypeByModel.map((entry: TypeMetric) => (
                  <tr key={`${entry.model}-${entry.eventType}`}>
                    <td>{entry.model}</td>
                    <td>{formatEventType(entry.eventType)}</td>
                    <td>{entry.count}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <div className="card section-gap">
        <h3>Top Retry Tasks by Model</h3>
        {summary.topRetryTasksByModel.length === 0 ? (
          <p className="empty">No retry tasks for this range.</p>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Task</th>
                  <th>Model</th>
                  <th>Tool</th>
                  <th>Retries</th>
                  <th>Retry Cost</th>
                </tr>
              </thead>
              <tbody>
                {summary.topRetryTasksByModel.map((task: TopTask) => (
                  <tr key={`${task.taskId}-${task.model}`}>
                    <td>
                      <Link to={`/tasks/${task.taskId}`}>{task.description}</Link>
                    </td>
                    <td>{task.model}</td>
                    <td>{formatSourceTool(task.sourceTool)}</td>
                    <td>{task.retryCount}</td>
                    <td>{formatUsd(task.retryCostUsd)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
