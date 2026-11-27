/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useMemo, useState } from 'react'
import {
  collectPresentKinds,
  formatAxisTime,
  formatSpanDuration,
  isRetrySpan,
  normalizeSpanKind,
  resolveSessionSpans,
  type SpanNode,
  type WaterfallKind,
} from '../utils/sessionSpans'
import { formatEventType, type TaskTimelineEvent } from '../utils/taskEvents'

type WaterfallFilter = 'all' | 'retries'

type SessionWaterfallProps = {
  spans?: SpanNode[] | null
  /** Fallback when GraphQL spanTree is empty/unavailable */
  events?: TaskTimelineEvent[] | null
  /** When true, omit the outer card wrapper (e.g. nested inside session replay) */
  embedded?: boolean
}

function spanDepth(span: SpanNode, byId: Map<string, SpanNode>): number {
  let depth = 0
  let parentId = span.parentId
  const seen = new Set<string>()
  while (parentId) {
    const key = String(parentId)
    if (seen.has(key)) break
    seen.add(key)
    const parent = byId.get(key)
    if (!parent) break
    depth += 1
    parentId = parent.parentId
  }
  return Math.min(depth, 3)
}

function buildAxisTicks(maxEnd: number): number[] {
  if (maxEnd <= 0) return [0]
  const niceSteps = [100, 250, 500, 1000, 2000, 5000, 10_000, 30_000, 60_000]
  const target = maxEnd / 4
  const step = niceSteps.find((s) => s >= target) ?? Math.ceil(maxEnd / 4)
  const ticks: number[] = [0]
  for (let t = step; t < maxEnd; t += step) {
    ticks.push(t)
  }
  if (ticks[ticks.length - 1] !== maxEnd) {
    ticks.push(maxEnd)
  }
  return ticks
}

function LegendSwatch({ kind, retry }: { kind?: WaterfallKind; retry?: boolean }) {
  if (retry) {
    return <span className="waterfall-legend-swatch waterfall-legend-swatch-retry" aria-hidden />
  }
  return (
    <span
      className="waterfall-legend-swatch"
      data-kind={kind ?? 'OTHER'}
      aria-hidden
    />
  )
}

export default function SessionWaterfall({ spans, events, embedded = false }: SessionWaterfallProps) {
  const [filter, setFilter] = useState<WaterfallFilter>('all')
  const [open, setOpen] = useState(true)
  const resolved = resolveSessionSpans(spans, events)
  const byId = new Map(resolved.map((span) => [String(span.id), span]))
  const maxEnd = Math.max(...resolved.map((s) => s.startMs + s.durationMs), 1)
  const retryCount = resolved.filter((s) => isRetrySpan(s.status)).length
  const visible = useMemo(
    () => (filter === 'retries' ? resolved.filter((s) => isRetrySpan(s.status)) : resolved),
    [filter, resolved],
  )
  const presentKinds = collectPresentKinds(visible)
  const ticks = buildAxisTicks(maxEnd)

  const body =
    resolved.length === 0 ? (
      <p className="empty">No session spans yet — events will appear here as the task progresses.</p>
    ) : (
      <div className="waterfall">
        <div className="waterfall-toolbar">
          <button
            type="button"
            className="timeline-toggle-btn"
            aria-expanded={open}
            onClick={() => setOpen((current) => !current)}
          >
            <span className="timeline-toggle-chevron" aria-hidden="true">
              {open ? '▾' : '▸'}
            </span>
            {open ? 'Hide waterfall' : 'Show waterfall'}
          </button>
          <div className="waterfall-meta" role="tablist" aria-label="Waterfall filter">
            <button
              type="button"
              role="tab"
              aria-selected={filter === 'all'}
              className={`timeline-filter-tab${filter === 'all' ? ' active' : ''}`}
              onClick={() => {
                setFilter('all')
                setOpen(true)
              }}
            >
              {resolved.length} span{resolved.length === 1 ? '' : 's'}
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={filter === 'retries'}
              className={`timeline-filter-tab waterfall-meta-retry${filter === 'retries' ? ' active' : ''}`}
              onClick={() => {
                setFilter('retries')
                setOpen(true)
              }}
            >
              {retryCount} retr{retryCount === 1 ? 'y' : 'ies'}
            </button>
            <span>{formatSpanDuration(maxEnd)} total</span>
          </div>
        </div>

        {open && (
          <>
            {visible.length === 0 ? (
              <p className="empty">No retry spans in this session.</p>
            ) : (
              <>
                <div className="waterfall-legend" aria-label="Waterfall legend">
                  {presentKinds.map((kind) => (
                    <span key={kind} className="waterfall-legend-item">
                      <LegendSwatch kind={kind} />
                      {formatEventType(kind)}
                    </span>
                  ))}
                  {retryCount > 0 && (
                    <span className="waterfall-legend-item waterfall-legend-item-retry">
                      <LegendSwatch retry />
                      Retry
                    </span>
                  )}
                </div>

                <div className="waterfall-chart">
                  <div className="waterfall-axis-spacer" aria-hidden />
                  <div className="waterfall-axis" role="presentation">
                    {ticks.map((tick) => (
                      <span
                        key={tick}
                        className="waterfall-axis-tick"
                        style={{ left: `${(tick / maxEnd) * 100}%` }}
                      >
                        {formatAxisTime(tick)}
                      </span>
                    ))}
                  </div>

                  <div className="waterfall-list">
                    {visible.map((span) => {
                      const kind = normalizeSpanKind(span.kind)
                      const retry = isRetrySpan(span.status)
                      const depth = spanDepth(span, byId)
                      const left = (span.startMs / maxEnd) * 100
                      const width = Math.max(1.25, (span.durationMs / maxEnd) * 100)
                      const title = [
                        span.name,
                        formatSpanDuration(span.durationMs),
                        `at ${formatSpanDuration(span.startMs)}`,
                        retry ? 'retry' : null,
                      ]
                        .filter(Boolean)
                        .join(' · ')

                      return (
                        <div
                          key={span.id}
                          className={`waterfall-row${retry ? ' is-retry' : ''}`}
                        >
                          <div
                            className="waterfall-label-cell"
                            style={{ paddingLeft: depth > 0 ? `${depth * 0.65}rem` : undefined }}
                          >
                            <div className="waterfall-label-row">
                              <span className="waterfall-label" title={span.name}>
                                {span.name}
                              </span>
                              {retry && <span className="waterfall-retry-badge">Retry</span>}
                            </div>
                            <span className="waterfall-duration">{formatSpanDuration(span.durationMs)}</span>
                          </div>
                          <div className="waterfall-track">
                            {retry && (
                              <span
                                className="waterfall-retry-marker"
                                style={{ left: `${left}%` }}
                                title="Retry event"
                                aria-hidden
                              />
                            )}
                            <div
                              className={`waterfall-bar${retry ? ' is-retry' : ''}`}
                              data-kind={kind}
                              style={{ left: `${left}%`, width: `${width}%` }}
                              title={title}
                            />
                          </div>
                        </div>
                      )
                    })}
                  </div>
                </div>
              </>
            )}
          </>
        )}
      </div>
    )

  if (embedded) {
    return (
      <div className="session-waterfall-embedded">
        <h4 className="session-waterfall-heading">Session waterfall</h4>
        <p className="page-subtitle">Relative timing of task events across the session</p>
        {body}
      </div>
    )
  }

  return (
    <div className="card section-gap">
      <h3>Session waterfall</h3>
      <p className="page-subtitle">Relative timing of task events across the session</p>
      {body}
    </div>
  )
}
