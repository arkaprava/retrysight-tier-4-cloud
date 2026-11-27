/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery } from '@apollo/client'
import { Link } from 'react-router-dom'
import TopicPatternHeatmap from '../components/TopicPatternHeatmap'
import { RECOMPUTE_TOPIC_PATTERNS, TOPIC_PATTERNS } from '../graphql/client'
import { useAuth } from '../auth/AuthContext'
import { usePlatformFeatures } from '../platformFeatures'

const WINDOW_OPTIONS = [
  { label: '7 days', value: 7 },
  { label: '14 days', value: 14 },
  { label: '30 days', value: 30 },
  { label: '60 days', value: 60 },
  { label: '90 days', value: 90 },
] as const

function shortId(id?: string | null): string {
  if (!id || id.length < 8) return id ?? '—'
  return `${id.slice(0, 8)}…`
}

type PatternRow = {
  id: string
  label: string
  taskCount: number
  failRate: number
  relevanceScore?: number
  suspicious?: boolean
  flagReason?: string | null
  sampleTaskIds?: string[] | null
  periodStart?: string | null
  periodEnd?: string | null
  windowDays?: number | null
  computedAt: string
}

function formatUtcDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { timeZone: 'UTC' })
}

function formatPeriodRange(periodStart: string, periodEnd: string): string {
  return `${formatUtcDate(periodStart)} – ${formatUtcDate(periodEnd)}`
}

function previewWindowRange(days: number): string {
  const end = new Date()
  const start = new Date(end)
  start.setUTCDate(start.getUTCDate() - days)
  return `${formatUtcDate(start.toISOString())} – ${formatUtcDate(end.toISOString())}`
}

function activeWindowDays(patterns: PatternRow[]): number | null {
  const first = patterns.find((p) => p.windowDays != null && p.windowDays > 0)
  if (first?.windowDays) return first.windowDays
  return null
}

export default function PatternsPage() {
  const { patternsEnabled, featuresLoading } = usePlatformFeatures()
  const { isAdmin, isSecadmin } = useAuth()
  const canRecompute = isAdmin || isSecadmin
  const [suspiciousOnly, setSuspiciousOnly] = useState(false)

  const { data, loading, error, refetch } = useQuery(TOPIC_PATTERNS, { skip: !patternsEnabled })
  const patterns: PatternRow[] = data?.topicPatterns ?? []

  const [windowDays, setWindowDays] = useState(30)
  const [appliedWindowDays, setAppliedWindowDays] = useState<number | null>(null)

  const computedWindowDays = useMemo(() => {
    if (patterns.length === 0) return appliedWindowDays ?? windowDays
    return activeWindowDays(patterns) ?? appliedWindowDays ?? windowDays
  }, [patterns, appliedWindowDays, windowDays])

  const [recompute, { loading: recomputing }] = useMutation(RECOMPUTE_TOPIC_PATTERNS, {
    onCompleted: () => {
      setAppliedWindowDays(windowDays)
      refetch()
    },
  })

  const suspiciousCount = useMemo(
    () => patterns.filter((p) => p.suspicious).length,
    [patterns],
  )
  const visiblePatterns = useMemo(
    () => (suspiciousOnly ? patterns.filter((p) => p.suspicious) : patterns),
    [patterns, suspiciousOnly],
  )

  const activePeriod = useMemo(() => {
    const first = patterns.find((p) => p.periodStart && p.periodEnd)
    if (!first?.periodStart || !first?.periodEnd) return null
    return formatPeriodRange(first.periodStart, first.periodEnd)
  }, [patterns])

  const windowStale = patterns.length > 0 && computedWindowDays !== windowDays

  useEffect(() => {
    if (patterns.length > 0 && appliedWindowDays === null) {
      const days = activeWindowDays(patterns) ?? 30
      setWindowDays(days)
      setAppliedWindowDays(days)
    }
  }, [patterns, appliedWindowDays])

  function applyWindow() {
    recompute({ variables: { windowDays } })
  }

  const analysisWindowLabel = useMemo(() => {
    if (patterns.length === 0) {
      return `Selected window: last ${windowDays} days (${previewWindowRange(windowDays)}). Click Apply to analyze.`
    }
    if (windowStale) {
      return `Active analysis: last ${computedWindowDays} days${activePeriod ? ` (${activePeriod})` : ''}. Selected: last ${windowDays} days (${previewWindowRange(windowDays)}).`
    }
    return `Analysis window: last ${computedWindowDays} days${activePeriod ? ` (${activePeriod})` : ''}.`
  }, [patterns.length, windowStale, computedWindowDays, activePeriod, windowDays])

  if (featuresLoading) {
    return (
      <div className="loading-state">
        <div className="loading-spinner" aria-hidden="true" />
        <span>Loading…</span>
      </div>
    )
  }

  if (!patternsEnabled) {
    return <p className="empty">Pattern clustering requires Tier 3+ with evaluations enabled.</p>
  }

  return (
    <div className="analytics-surface">
      <header className="page-header">
        <h2 className="page-title">Topic Patterns</h2>
        <p className="page-subtitle">
          Recurring session themes clustered from task descriptions.
          {' '}
          {analysisWindowLabel}
          {suspiciousCount > 0 && (
            <> {suspiciousCount} suspicious pattern{suspiciousCount === 1 ? '' : 's'} flagged as non-engineering.</>
          )}
        </p>
        <div className="patterns-toolbar">
          <label className="patterns-window-select">
            <span>Pattern window</span>
            <select
              value={windowDays}
              onChange={(e) => setWindowDays(Number(e.target.value))}
              disabled={recomputing}
            >
              {WINDOW_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
              ))}
            </select>
          </label>
          {canRecompute && (
            <button
              type="button"
              className="btn-secondary"
              disabled={recomputing}
              onClick={applyWindow}
            >
              {recomputing ? 'Recomputing…' : 'Apply window & recompute'}
            </button>
          )}
          <label className="patterns-filter-toggle">
            <input
              type="checkbox"
              checked={suspiciousOnly}
              onChange={(e) => setSuspiciousOnly(e.target.checked)}
            />
            Show suspicious only
          </label>
        </div>
        {windowStale && (
          <p className="page-subtitle pattern-window-hint">
            Patterns were computed with a {computedWindowDays}-day window.
            {canRecompute ? ' Click Apply to recompute with the selected window.' : ' Ask an admin to recompute.'}
          </p>
        )}
        {!canRecompute && patterns.length === 0 && (
          <p className="page-subtitle pattern-window-hint">
            Patterns are computed nightly. An admin can recompute on demand with a custom window.
          </p>
        )}
      </header>

      {loading ? (
        <p className="empty">Loading patterns…</p>
      ) : error ? (
        <p className="empty">Failed to load patterns: {error.message}</p>
      ) : patterns.length === 0 ? (
        <p className="empty">
          No patterns yet for the selected window.
          {canRecompute && ' Click "Apply window & recompute" to cluster tasks from that period.'}
        </p>
      ) : visiblePatterns.length === 0 ? (
        <p className="empty">No suspicious patterns found in the current cluster set.</p>
      ) : (
        <>
          <div className="card section-gap">
            <h3>Pattern heatmap</h3>
            <p className="pattern-heatmap-subtitle">
              Color intensity shows session volume and eval fail rate. Flagged rows look unrelated to software engineering.
            </p>
            <TopicPatternHeatmap patterns={visiblePatterns} />
          </div>

          <div className="card section-gap">
            <h3>{suspiciousOnly ? 'Suspicious patterns' : 'All patterns'}</h3>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Topic</th>
                    <th>Status</th>
                    <th>Relevance</th>
                    <th>Sessions</th>
                    <th>Fail rate</th>
                    <th>Sample tasks</th>
                    <th>Computed</th>
                  </tr>
                </thead>
                <tbody>
                  {visiblePatterns.map((p) => (
                    <tr key={p.id} className={p.suspicious ? 'pattern-row-suspicious' : undefined}>
                      <td>{p.label}</td>
                      <td>
                        {p.suspicious ? (
                          <span className="pattern-flag-badge table" title={p.flagReason ?? undefined}>Suspicious</span>
                        ) : (
                          <span className="pattern-ok-badge">Engineering</span>
                        )}
                      </td>
                      <td>{Number.isFinite(p.relevanceScore) ? `${Math.round((p.relevanceScore ?? 0) * 100)}%` : '—'}</td>
                      <td>{p.taskCount}</td>
                      <td>{Number.isFinite(p.failRate) ? `${(p.failRate * 100).toFixed(0)}%` : '—'}</td>
                      <td>
                        {(p.sampleTaskIds ?? []).slice(0, 3).map((id) => (
                          <Link key={id} to={`/tasks/${id}`} className="chip-link">{shortId(id)}</Link>
                        ))}
                      </td>
                      <td>{new Date(p.computedAt).toLocaleString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  )
}
