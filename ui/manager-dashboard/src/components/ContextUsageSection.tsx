/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useState } from 'react'
import AnalyticsChart from './analytics/AnalyticsChart'
import { formatEventTime } from '../utils/taskEvents'
import type { TaskTimelineEvent } from '../utils/taskEvents'
import {
  buildContextChartData,
  computeContextFillPercent,
  computeLatestContextTokens,
  computePeakContextTokens,
  enrichCompactionEvents,
  extractCompactionEvents,
  formatFillPercent,
  formatTokenCount,
  formatUsd,
  hasContextUsageData,
  resolveContextWindow,
  resolveModel,
  structuredMetadataEntries,
  sumTokenCategories,
  turnContextTokens,
  turnTotalTokens,
  type TokenTurn,
  type TokenUsageSummary,
} from '../utils/contextUsage'

type ContextUsageSectionProps = {
  metadata: Record<string, unknown> | null
  tokenUsage: TokenUsageSummary
  tokenTurns: TokenTurn[]
  timeline: TaskTimelineEvent[]
}

type TokenBreakdownItem = {
  key: string
  label: string
  value: number
  color: string
}

function ContextMeter({
  used,
  limit,
  label,
}: {
  used: number
  limit: number
  label: string
}) {
  const percent = computeContextFillPercent(used, limit) ?? 0
  const tone = percent >= 90 ? 'danger' : percent >= 70 ? 'warning' : 'info'

  return (
    <div className="context-meter">
      <div className="context-meter-header">
        <span className="context-meter-label">{label}</span>
        <span className="context-meter-values">
          <strong>{formatTokenCount(used)}</strong>
          <span className="context-meter-separator">/</span>
          <span>{formatTokenCount(limit)} tokens</span>
          <span className={`context-meter-percent ${tone}`}>{formatFillPercent(percent)}</span>
        </span>
      </div>
      <div className="context-meter-track" role="progressbar" aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100}>
        <div className={`context-meter-fill ${tone}`} style={{ width: `${percent}%` }} />
      </div>
    </div>
  )
}

function TokenBreakdownGrid({ items }: { items: TokenBreakdownItem[] }) {
  const visible = items.filter((item) => item.value > 0)
  if (visible.length === 0) {
    return <p className="empty">No token usage recorded.</p>
  }

  return (
    <div className="context-token-grid">
      {visible.map((item) => (
        <div key={item.key} className="context-token-item">
          <span className="context-token-dot" style={{ backgroundColor: item.color }} aria-hidden="true" />
          <div>
            <span className="context-token-label">{item.label}</span>
            <strong className="context-token-value">{formatTokenCount(item.value)}</strong>
          </div>
        </div>
      ))}
    </div>
  )
}

export default function ContextUsageSection({
  metadata,
  tokenUsage,
  tokenTurns,
  timeline,
}: ContextUsageSectionProps) {
  const [showPerTurnDetails, setShowPerTurnDetails] = useState(false)

  const model = resolveModel(metadata, tokenTurns)
  const contextWindow = resolveContextWindow(metadata)
  const compactions = enrichCompactionEvents(extractCompactionEvents(timeline), tokenTurns)
  const compactionTurns = new Set(
    compactions
      .map((event) => event.nearestTurn)
      .filter((turn): turn is number => turn != null),
  )
  const chartData = buildContextChartData(tokenTurns, compactionTurns, contextWindow)
  const peakContext = computePeakContextTokens(tokenTurns)
  const latestContext = computeLatestContextTokens(tokenTurns)
  const metadataEntries = structuredMetadataEntries(metadata)
  const totalTokens = sumTokenCategories(tokenUsage)
  const estimatedCost = formatUsd(tokenUsage.estimatedCostUsd)

  if (!hasContextUsageData(tokenUsage, tokenTurns, metadata, compactions)) {
    return null
  }

  const breakdownItems: TokenBreakdownItem[] = [
    { key: 'input', label: 'Input', value: tokenUsage.inputTokens, color: '#6b5bcc' },
    { key: 'output', label: 'Output', value: tokenUsage.outputTokens, color: '#a78bfa' },
    { key: 'cacheRead', label: 'Cache read', value: tokenUsage.cacheReadTokens, color: '#34d399' },
    { key: 'cacheWrite', label: 'Cache write', value: tokenUsage.cacheWriteTokens, color: '#e4c04a' },
    { key: 'reasoning', label: 'Reasoning', value: tokenUsage.reasoningTokens, color: '#f472b6' },
  ]

  const sortedTurns = [...tokenTurns].sort((a, b) => a.turnIndex - b.turnIndex)

  return (
    <div className="card section-gap context-usage-section">
      <div className="context-usage-header">
        <div>
          <h3>Context Usage</h3>
          <p className="context-usage-subtitle">
            Token and context window details for this task
          </p>
        </div>
        <div className="context-usage-badges">
          {model && <span className="context-badge model">{model}</span>}
          {contextWindow && (
            <span className="context-badge window">
              {formatTokenCount(contextWindow)} ctx window
            </span>
          )}
          {compactions.length > 0 && (
            <span className="context-badge compaction">
              {compactions.length} compaction{compactions.length === 1 ? '' : 's'}
            </span>
          )}
        </div>
      </div>

      {contextWindow ? (
        <div className="context-meter-group">
          <ContextMeter used={latestContext} limit={contextWindow} label="Latest turn context" />
          {peakContext !== latestContext && (
            <ContextMeter used={peakContext} limit={contextWindow} label="Peak context" />
          )}
        </div>
      ) : (
        <div className="context-meter-fallback">
          <div>
            <span className="context-meter-label">Context tokens (latest turn)</span>
            <strong>{formatTokenCount(latestContext)}</strong>
          </div>
          {peakContext !== latestContext && (
            <div>
              <span className="context-meter-label">Peak context tokens</span>
              <strong>{formatTokenCount(peakContext)}</strong>
            </div>
          )}
          <p className="context-meter-note">
            Context window limit not recorded for this task. Peak and latest values use input + cache + reasoning per turn.
          </p>
        </div>
      )}

      <div className="context-summary-row">
        <div>
          <span className="context-summary-label">Total tokens</span>
          <strong className="context-summary-value">{formatTokenCount(totalTokens)}</strong>
        </div>
        {estimatedCost && (
          <div>
            <span className="context-summary-label">Est. cost</span>
            <strong className="context-summary-value">{estimatedCost}</strong>
          </div>
        )}
        <div>
          <span className="context-summary-label">Turns</span>
          <strong className="context-summary-value">{sortedTurns.length}</strong>
        </div>
      </div>

      <div className="context-usage-block">
        <h4>Token breakdown</h4>
        <TokenBreakdownGrid items={breakdownItems} />
      </div>

      {chartData.length > 0 && (
        <div className="context-usage-block section-gap">
          <AnalyticsChart
            title="Per-turn usage"
            type="line"
            data={chartData}
            index="turn"
            categories={['input', 'output', 'cacheRead', 'cacheWrite', 'reasoning', 'contextUsed']}
            colors={['indigo', 'cyan', 'emerald', 'amber', 'orange', 'violet']}
            valueFormatter={(value) => formatTokenCount(value)}
            align="start"
            footer={
              compactions.length > 0 ? (
                <p className="context-chart-legend">
                  Compaction events occurred near turns marked in the table below.
                </p>
              ) : undefined
            }
          />
        </div>
      )}

      {sortedTurns.length > 0 && (
        <div className="context-usage-block">
          <h4>Per-turn details</h4>
          <button
            type="button"
            className="timeline-expand-btn"
            onClick={() => setShowPerTurnDetails((current) => !current)}
            aria-expanded={showPerTurnDetails}
          >
            {showPerTurnDetails ? 'Hide per-turn details' : 'Show per-turn details'}
          </button>
          {showPerTurnDetails && (
            <div className="table-wrap">
              <table className="table context-turn-table">
                <thead>
                  <tr>
                    <th>Turn</th>
                    <th>Model</th>
                    <th>Input</th>
                    <th>Output</th>
                    <th>Cache R</th>
                    <th>Cache W</th>
                    <th>Reasoning</th>
                    <th>Context</th>
                    <th>Total</th>
                    {contextWindow && <th>Fill</th>}
                    <th>Cost</th>
                    <th>Recorded</th>
                  </tr>
                </thead>
                <tbody>
                  {sortedTurns.map((turn) => {
                    const contextTokens = turnContextTokens(turn)
                    const fill = formatFillPercent(computeContextFillPercent(contextTokens, contextWindow))
                    const cost = formatUsd(turn.estimatedCostUsd)
                    const isCompactionTurn = compactionTurns.has(turn.turnIndex)

                    return (
                      <tr key={turn.turnIndex} className={isCompactionTurn ? 'compaction-turn' : undefined}>
                        <td>
                          <strong>#{turn.turnIndex}</strong>
                          {isCompactionTurn && <span className="compaction-marker" title="Near compaction">⊘</span>}
                        </td>
                        <td>{turn.model ?? '—'}</td>
                        <td>{formatTokenCount(turn.inputTokens)}</td>
                        <td>{formatTokenCount(turn.outputTokens)}</td>
                        <td>{formatTokenCount(turn.cacheReadTokens ?? 0)}</td>
                        <td>{formatTokenCount(turn.cacheWriteTokens ?? 0)}</td>
                        <td>{formatTokenCount(turn.reasoningTokens ?? 0)}</td>
                        <td>{formatTokenCount(contextTokens)}</td>
                        <td>{formatTokenCount(turnTotalTokens(turn))}</td>
                        {contextWindow && <td>{fill}</td>}
                        <td>{cost ?? '—'}</td>
                        <td>{turn.recordedAt ? formatEventTime(turn.recordedAt) : '—'}</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {compactions.length > 0 && (
        <div className="context-usage-block">
          <h4>Compaction events</h4>
          <ul className="compaction-list">
            {compactions.map((event) => (
              <li key={event.id}>
                <span className="compaction-marker" aria-hidden="true">⊘</span>
                <span>{formatEventTime(event.occurredAt)}</span>
                <span className="muted">#{event.sequenceNum}</span>
                {event.nearestTurn != null && (
                  <span className="compaction-turn-link">near turn {event.nearestTurn}</span>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}

      {metadataEntries.length > 0 && (
        <div className="context-usage-block">
          <h4>Session metadata</h4>
          <div className="report-summary context-metadata-grid">
            {metadataEntries.map((entry) => (
              <div key={entry.key}>
                <span>{entry.label}</span>
                <strong>{entry.value}</strong>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
