/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useEffect, useMemo, useState } from 'react'
import {
  eventDetailLines,
  eventPreviewBlocks,
  formatEventTime,
  formatEventType,
  formatPayloadJson,
  formatTokenUsage,
  getActionSeverity,
  getEventSeverity,
  hasExpandablePayload,
  inferActionLabel,
  summarizePayload,
  type TaskTimelineEvent,
} from '../utils/taskEvents'

export type TimelineFilter = 'all' | 'retries'

type TaskEventTimelineProps = {
  events: TaskTimelineEvent[]
  /** Controlled filter; when set, parent owns selection (e.g. Retries metric click). */
  filter?: TimelineFilter
  onFilterChange?: (filter: TimelineFilter) => void
  /** Section open/closed; defaults to open (toggleable). */
  open?: boolean
  onOpenChange?: (open: boolean) => void
  defaultOpen?: boolean
}

export default function TaskEventTimeline({
  events,
  filter: controlledFilter,
  onFilterChange,
  open: controlledOpen,
  onOpenChange,
  defaultOpen = true,
}: TaskEventTimelineProps) {
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set())
  const [internalFilter, setInternalFilter] = useState<TimelineFilter>('all')
  const [internalOpen, setInternalOpen] = useState(defaultOpen)

  const filter = controlledFilter ?? internalFilter
  const open = controlledOpen ?? internalOpen

  useEffect(() => {
    if (controlledFilter != null) {
      setInternalFilter(controlledFilter)
    }
  }, [controlledFilter])

  const retryCount = useMemo(() => events.filter((event) => event.retry).length, [events])
  const visibleEvents = useMemo(
    () => (filter === 'retries' ? events.filter((event) => event.retry) : events),
    [events, filter],
  )

  function setFilter(next: TimelineFilter) {
    if (controlledFilter == null) {
      setInternalFilter(next)
    }
    onFilterChange?.(next)
  }

  function setOpen(next: boolean) {
    if (controlledOpen == null) {
      setInternalOpen(next)
    }
    onOpenChange?.(next)
  }

  function toggleExpanded(eventId: string) {
    setExpandedIds((current) => {
      const next = new Set(current)
      if (next.has(eventId)) {
        next.delete(eventId)
      } else {
        next.add(eventId)
      }
      return next
    })
  }

  if (events.length === 0) {
    return <p className="empty">No events recorded.</p>
  }

  return (
    <div className="timeline">
      <div className="timeline-toolbar">
        <button
          type="button"
          className="timeline-toggle-btn"
          aria-expanded={open}
          onClick={() => setOpen(!open)}
        >
          <span className="timeline-toggle-chevron" aria-hidden="true">
            {open ? '▾' : '▸'}
          </span>
          {open ? 'Hide timeline' : 'Show timeline'}
        </button>
        <div className="timeline-summary" role="tablist" aria-label="Timeline filter">
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
            {events.length} events
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={filter === 'retries'}
            className={`timeline-filter-tab timeline-summary-retry${filter === 'retries' ? ' active' : ''}`}
            onClick={() => {
              setFilter('retries')
              setOpen(true)
            }}
          >
            {retryCount} {retryCount === 1 ? 'retry' : 'retries'}
          </button>
        </div>
      </div>

      {open && (
        <>
          {visibleEvents.length === 0 ? (
            <p className="empty">
              {filter === 'retries' ? 'No retry events in this timeline.' : 'No events recorded.'}
            </p>
          ) : (
            <ol className="timeline-list">
              {visibleEvents.map((event) => {
                const payloadSummary = summarizePayload(event.payload)
                const tokenSummary = formatTokenUsage(event.tokenUsage)
                const detailLines = eventDetailLines(event.payload)
                const previewBlocks = eventPreviewBlocks(event.payload)
                const expandable = hasExpandablePayload(event.payload)
                const expanded = expandedIds.has(event.id)
                const actionLabel = inferActionLabel(event.eventType, event.payload)
                const rawPayload = formatPayloadJson(event.payload)
                const severity = getEventSeverity(event.eventType, event.retry, event.payload)
                const actionSeverity = getActionSeverity(event.eventType, event.payload)

                return (
                  <li
                    key={event.id}
                    className={`timeline-item severity-${severity}${event.retry ? ' retry' : ''}`}
                  >
                    <div className="timeline-marker" aria-hidden="true" />
                    <div className="timeline-content">
                      <div className="timeline-header">
                        <span className={`timeline-type severity-${severity}${event.retry ? ' retry' : ''}`}>
                          {formatEventType(event.eventType)}
                        </span>
                        {actionLabel && (
                          <span className={`timeline-action-badge severity-${actionSeverity}`}>
                            {actionLabel}
                          </span>
                        )}
                        {event.retry && <span className="timeline-retry-badge">Retry</span>}
                        {event.tokenUsage?.model && (
                          <span className="timeline-model-badge">{event.tokenUsage.model}</span>
                        )}
                        <span className="timeline-seq">#{event.sequenceNum}</span>
                        {tokenSummary ? (
                          <span className="timeline-tokens">{tokenSummary}</span>
                        ) : (
                          <span className="timeline-tokens muted">—</span>
                        )}
                      </div>
                      <time className="timeline-time" dateTime={event.occurredAt}>
                        {formatEventTime(event.occurredAt)}
                      </time>
                      {payloadSummary && (
                        <p className="timeline-payload">{payloadSummary}</p>
                      )}
                      {detailLines.length > 0 && (
                        <ul className="timeline-details">
                          {detailLines.map((line) => (
                            <li key={line}>{line}</li>
                          ))}
                        </ul>
                      )}
                      {expandable && (
                        <button
                          type="button"
                          className="timeline-expand-btn"
                          onClick={() => toggleExpanded(event.id)}
                        >
                          {expanded ? 'Hide change details' : 'Show change details'}
                        </button>
                      )}
                      {expanded && previewBlocks.length > 0 && (
                        <div className="timeline-previews">
                          {previewBlocks.map((block) => (
                            <div key={block.label} className="timeline-preview-block">
                              <span className="timeline-preview-label">{block.label}</span>
                              <pre className="timeline-preview-content">{block.value}</pre>
                            </div>
                          ))}
                        </div>
                      )}
                      {expanded && previewBlocks.length === 0 && rawPayload && (
                        <div className="timeline-previews">
                          <div className="timeline-preview-block">
                            <span className="timeline-preview-label">Event data</span>
                            <pre className="timeline-preview-content">{rawPayload}</pre>
                          </div>
                        </div>
                      )}
                    </div>
                  </li>
                )
              })}
            </ol>
          )}
        </>
      )}
    </div>
  )
}
