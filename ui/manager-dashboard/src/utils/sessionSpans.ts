/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { formatEventType, type TaskTimelineEvent } from './taskEvents'

export type SpanNode = {
  id: string
  name: string
  kind: string
  startMs: number
  durationMs: number
  status: string
  parentId?: string | null
}

/** Stable CSS kind tokens for waterfall bar colors (must match global.css). */
export const WATERFALL_KIND_ORDER = [
  'SESSION_START',
  'EDIT',
  'DIFF_ACCEPTED',
  'DIFF_REJECTED',
  'TEST_FAIL',
  'SUBAGENT',
  'COMPACTION',
  'TOKEN_USAGE',
  'SESSION_END',
] as const

export type WaterfallKind = (typeof WATERFALL_KIND_ORDER)[number] | 'OTHER'

const LABEL_TO_KIND: Record<string, WaterfallKind> = {
  EDIT: 'EDIT',
  'TEST FAILED': 'TEST_FAIL',
  TEST_FAILED: 'TEST_FAIL',
  'DIFF REJECTED': 'DIFF_REJECTED',
  'DIFF ACCEPTED': 'DIFF_ACCEPTED',
  SUBAGENT: 'SUBAGENT',
  'TOKEN USAGE': 'TOKEN_USAGE',
  'SESSION START': 'SESSION_START',
  'SESSION END': 'SESSION_END',
  COMPACTION: 'COMPACTION',
}

export function normalizeSpanKind(kindOrName: string | null | undefined): WaterfallKind {
  if (!kindOrName) return 'OTHER'
  const upper = kindOrName.trim().toUpperCase()
  const underscored = upper.replace(/[\s-]+/g, '_')
  if ((WATERFALL_KIND_ORDER as readonly string[]).includes(underscored)) {
    return underscored as WaterfallKind
  }
  return LABEL_TO_KIND[upper] ?? LABEL_TO_KIND[underscored] ?? 'OTHER'
}

export function isRetrySpan(status: string | null | undefined): boolean {
  return (status ?? '').toLowerCase() === 'retry'
}

export function formatSpanDuration(ms: number): string {
  if (!Number.isFinite(ms) || ms < 0) return '0ms'
  if (ms < 1000) return `${Math.round(ms)}ms`
  if (ms < 10_000) return `${(ms / 1000).toFixed(1)}s`
  return `${Math.round(ms / 1000)}s`
}

export function formatAxisTime(ms: number): string {
  return formatSpanDuration(ms)
}

/** Build waterfall spans from a task timeline (mirrors manager spanTree derivation). */
export function buildSpansFromTimeline(events: TaskTimelineEvent[] | null | undefined): SpanNode[] {
  if (!events || events.length === 0) {
    return []
  }

  const sorted = [...events].sort((a, b) => {
    const byTime = new Date(a.occurredAt).getTime() - new Date(b.occurredAt).getTime()
    if (byTime !== 0) return byTime
    return a.sequenceNum - b.sequenceNum
  })

  const baseMs = new Date(sorted[0].occurredAt).getTime()
  if (Number.isNaN(baseMs)) {
    return []
  }

  const rootId = sorted[0].id
  return sorted.map((event, i) => {
    const startMs = Math.max(0, new Date(event.occurredAt).getTime() - baseMs)
    const nextMs =
      i + 1 < sorted.length
        ? new Date(sorted[i + 1].occurredAt).getTime() - baseMs
        : startMs + 250
    const durationMs = Math.max(50, nextMs - startMs)
    const kind = normalizeSpanKind(event.eventType)
    return {
      id: event.id,
      name: formatEventType(event.eventType),
      kind,
      startMs,
      durationMs,
      status: event.retry ? 'retry' : 'ok',
      parentId: i === 0 ? null : rootId,
    }
  })
}

export function resolveSessionSpans(
  spans: SpanNode[] | null | undefined,
  events?: TaskTimelineEvent[] | null,
): SpanNode[] {
  const retryById = new Map<string, boolean>()
  if (events) {
    for (const event of events) {
      retryById.set(String(event.id), Boolean(event.retry))
    }
  }

  if (spans && spans.length > 0) {
    return spans.map((span) => {
      const kind = normalizeSpanKind(span.kind || span.name)
      const fromTimeline = retryById.get(String(span.id))
      const retry = isRetrySpan(span.status) || fromTimeline === true
      return {
        ...span,
        kind,
        name: formatEventType(kind === 'OTHER' ? span.name : kind),
        status: retry ? 'retry' : span.status === 'ok' || !span.status ? 'ok' : span.status,
      }
    })
  }

  return buildSpansFromTimeline(events)
}

export function collectPresentKinds(spans: SpanNode[]): WaterfallKind[] {
  const seen = new Set<WaterfallKind>()
  for (const span of spans) {
    seen.add(normalizeSpanKind(span.kind))
  }
  const ordered: WaterfallKind[] = WATERFALL_KIND_ORDER.filter((kind) => seen.has(kind))
  if (seen.has('OTHER')) ordered.push('OTHER')
  return ordered
}
