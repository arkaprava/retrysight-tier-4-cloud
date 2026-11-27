/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useMemo } from 'react'

export type TopicPatternRow = {
  id: string
  label: string
  taskCount: number
  failRate: number
  relevanceScore?: number
  suspicious?: boolean
  flagReason?: string | null
}

type Props = {
  patterns: TopicPatternRow[]
}

function clamp01(value: number): number {
  if (!Number.isFinite(value)) return 0
  return Math.max(0, Math.min(1, value))
}

/** Green (low) → amber → red (high) for eval fail rate. */
function failRateStyle(rate: number): { backgroundColor: string; color: string } {
  const t = clamp01(rate)
  if (t <= 0.5) {
    const mix = t * 2
    return {
      backgroundColor: `rgba(${Math.round(52 + (251 - 52) * mix)}, ${Math.round(211 + (191 - 211) * mix)}, ${Math.round(153 + (36 - 153) * mix)}, ${0.18 + mix * 0.12})`,
      color: 'var(--text-primary)',
    }
  }
  const mix = (t - 0.5) * 2
  return {
    backgroundColor: `rgba(${Math.round(251 + (248 - 251) * mix)}, ${Math.round(191 + (113 - 191) * mix)}, ${Math.round(36 + (113 - 36) * mix)}, ${0.22 + mix * 0.18})`,
    color: 'var(--text-primary)',
  }
}

function volumeStyle(count: number, max: number): { backgroundColor: string } {
  const intensity = max > 0 ? clamp01(count / max) : 0
  return {
    backgroundColor: `rgba(99, 102, 241, ${0.08 + intensity * 0.42})`,
  }
}

export default function TopicPatternHeatmap({ patterns }: Props) {
  const rows = useMemo(() => {
    return [...patterns]
      .sort((a, b) => b.taskCount - a.taskCount || b.failRate - a.failRate)
      .slice(0, 20)
  }, [patterns])

  const maxTasks = useMemo(
    () => rows.reduce((max, row) => Math.max(max, row.taskCount), 0),
    [rows],
  )

  if (rows.length === 0) {
    return null
  }

  return (
    <div className="pattern-heatmap" role="img" aria-label="Topic pattern heatmap by session volume and eval fail rate">
      <div className="pattern-heatmap-header">
        <span className="pattern-heatmap-corner" />
        <span className="pattern-heatmap-col-title">Session volume</span>
        <span className="pattern-heatmap-col-title">Eval fail rate</span>
        <span className="pattern-heatmap-col-title">Relevance</span>
      </div>

      <div className="pattern-heatmap-body">
        {rows.map((row) => {
          const failPct = clamp01(row.failRate)
          const volumePct = maxTasks > 0 ? Math.round((row.taskCount / maxTasks) * 100) : 0
          return (
            <div
              key={row.id}
              className={`pattern-heatmap-row${row.suspicious ? ' pattern-heatmap-row-suspicious' : ''}`}
            >
              <div className="pattern-heatmap-label" title={row.label}>
                {row.suspicious && <span className="pattern-flag-badge" title={row.flagReason ?? 'Suspicious'}>⚠</span>}
                {row.label}
              </div>
              <div
                className="pattern-heatmap-cell pattern-heatmap-cell-volume"
                style={volumeStyle(row.taskCount, maxTasks)}
                title={`${row.taskCount} sessions (${volumePct}% of max)`}
              >
                <span className="pattern-heatmap-cell-value">{row.taskCount}</span>
              </div>
              <div
                className="pattern-heatmap-cell pattern-heatmap-cell-fail"
                style={failRateStyle(failPct)}
                title={`${(failPct * 100).toFixed(0)}% eval fail rate`}
              >
                <span className="pattern-heatmap-cell-value">{(failPct * 100).toFixed(0)}%</span>
              </div>
              <div
                className={`pattern-heatmap-cell pattern-heatmap-cell-relevance${row.suspicious ? ' suspicious' : ''}`}
                title={row.flagReason ?? `${Math.round((row.relevanceScore ?? 0) * 100)}% software engineering relevance`}
              >
                <span className="pattern-heatmap-cell-value">
                  {row.suspicious ? 'Flagged' : `${Math.round((row.relevanceScore ?? 0) * 100)}%`}
                </span>
              </div>
            </div>
          )
        })}
      </div>

      <div className="pattern-heatmap-legend">
        <div className="pattern-heatmap-legend-group">
          <span className="pattern-heatmap-legend-label">Volume</span>
          <div className="pattern-heatmap-legend-bar pattern-heatmap-legend-bar-volume" />
          <span className="pattern-heatmap-legend-hint">Low → high sessions</span>
        </div>
        <div className="pattern-heatmap-legend-group">
          <span className="pattern-heatmap-legend-label">Fail rate</span>
          <div className="pattern-heatmap-legend-bar pattern-heatmap-legend-bar-fail" />
          <span className="pattern-heatmap-legend-hint">Low → high eval failures</span>
        </div>
      </div>
    </div>
  )
}
