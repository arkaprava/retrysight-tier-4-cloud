/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

export type EventTokenUsage = {
  inputTokens: number
  outputTokens: number
  cacheReadTokens?: number
  cacheWriteTokens?: number
  reasoningTokens?: number
  totalTokens: number
  estimatedCostUsd?: number | null
  model?: string | null
}

export type TaskTimelineEvent = {
  id: string
  eventType: string
  occurredAt: string
  retry: boolean
  sequenceNum: number
  payload?: string | null
  tokenUsage?: EventTokenUsage | null
}

export type ParsedEventPayload = {
  action?: string
  changeSummary?: string
  changeType?: string
  filePath?: string
  tool?: string
  editOrdinal?: number
  oldPreview?: string
  newPreview?: string
  patchPreview?: string
  linesAdded?: number
  linesRemoved?: number
  reason?: string
  userFeedback?: string
  status?: string
  turn?: number
  context?: string
  command?: string
  exitCode?: number
  outputPreview?: string
  affectedFiles?: string[]
}

const EVENT_LABELS: Record<string, string> = {
  EDIT: 'Edit',
  TEST_FAIL: 'Test Failed',
  DIFF_REJECTED: 'Diff Rejected',
  DIFF_ACCEPTED: 'Diff Accepted',
  SUBAGENT: 'Subagent',
  TOKEN_USAGE: 'Token Usage',
  SESSION_START: 'Session Start',
  SESSION_END: 'Session End',
  COMPACTION: 'Compaction',
}

const ACTION_LABELS: Record<string, string> = {
  initial_edit: 'Initial edit',
  edit: 'Edit',
  rewrite: 'Rewrite',
  rejected: 'Rejected',
  accepted: 'Accepted',
  test_failed: 'Test failed',
}

export function formatEventType(eventType: string): string {
  return EVENT_LABELS[eventType] ?? eventType.replace(/_/g, ' ')
}

export function formatActionLabel(action?: string): string | null {
  if (!action) return null
  return ACTION_LABELS[action] ?? action.replace(/_/g, ' ')
}

export function formatEventTime(occurredAt: string): string {
  const date = new Date(occurredAt)
  if (Number.isNaN(date.getTime())) {
    return occurredAt
  }
  return date.toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

export function formatTokenUsage(usage?: EventTokenUsage | null): string | null {
  if (!usage || usage.totalTokens === 0) {
    return null
  }

  const parts: string[] = []
  if (usage.inputTokens > 0 || usage.outputTokens > 0) {
    if (usage.inputTokens > 0) {
      parts.push(`${usage.inputTokens.toLocaleString()} in`)
    }
    if (usage.outputTokens > 0) {
      parts.push(`${usage.outputTokens.toLocaleString()} out`)
    }
  } else {
    parts.push(`${usage.totalTokens.toLocaleString()} tokens`)
  }

  if (usage.cacheReadTokens && usage.cacheReadTokens > 0) {
    parts.push(`${usage.cacheReadTokens.toLocaleString()} cache read`)
  }
  if (usage.cacheWriteTokens && usage.cacheWriteTokens > 0) {
    parts.push(`${usage.cacheWriteTokens.toLocaleString()} cache write`)
  }
  if (usage.reasoningTokens && usage.reasoningTokens > 0) {
    parts.push(`${usage.reasoningTokens.toLocaleString()} reasoning`)
  }

  if (usage.estimatedCostUsd != null && usage.estimatedCostUsd > 0) {
    parts.push(`$${usage.estimatedCostUsd.toFixed(4)}`)
  }
  if (usage.model) {
    parts.push(usage.model)
  }

  return parts.join(' / ')
}

export function parseEventPayload(payload?: string | null): ParsedEventPayload | null {
  if (!payload) return null

  try {
    return JSON.parse(payload) as ParsedEventPayload
  } catch {
    return null
  }
}

export type EventSeverity = 'critical' | 'high' | 'medium' | 'low' | 'info'

const ACTION_SEVERITY: Record<string, EventSeverity> = {
  test_failed: 'critical',
  rejected: 'critical',
  rewrite: 'high',
  accepted: 'low',
  initial_edit: 'info',
  edit: 'info',
}

const EVENT_SEVERITY: Record<string, EventSeverity> = {
  TEST_FAIL: 'critical',
  DIFF_REJECTED: 'high',
  COMPACTION: 'medium',
  DIFF_ACCEPTED: 'low',
  EDIT: 'info',
  SUBAGENT: 'info',
  TOKEN_USAGE: 'info',
  SESSION_START: 'info',
  SESSION_END: 'info',
}

function bumpSeverityForRetry(severity: EventSeverity): EventSeverity {
  if (severity === 'info' || severity === 'low') {
    return 'high'
  }
  if (severity === 'medium') {
    return 'high'
  }
  return severity
}

export function getEventSeverity(
  eventType: string,
  retry: boolean,
  payload?: string | null,
): EventSeverity {
  const data = parseEventPayload(payload)
  const actionSeverity = data?.action ? ACTION_SEVERITY[data.action] : undefined
  let severity = actionSeverity ?? EVENT_SEVERITY[eventType] ?? 'info'

  if (retry) {
    severity = bumpSeverityForRetry(severity)
  }

  return severity
}

export function getActionSeverity(
  eventType: string,
  payload?: string | null,
): EventSeverity {
  const data = parseEventPayload(payload)
  if (data?.action && ACTION_SEVERITY[data.action]) {
    return ACTION_SEVERITY[data.action]
  }

  switch (eventType) {
    case 'TEST_FAIL':
      return 'critical'
    case 'DIFF_REJECTED':
      return 'high'
    case 'DIFF_ACCEPTED':
      return 'low'
    case 'COMPACTION':
      return 'medium'
    default:
      return 'info'
  }
}

export function inferActionLabel(eventType: string, payload?: string | null): string | null {
  const data = parseEventPayload(payload)
  const explicit = formatActionLabel(data?.action)
  if (explicit) return explicit

  switch (eventType) {
    case 'EDIT':
      return 'Edit'
    case 'DIFF_REJECTED':
      return 'Rejected'
    case 'DIFF_ACCEPTED':
      return 'Accepted'
    case 'TEST_FAIL':
      return 'Test failed'
    default:
      return null
  }
}

export function summarizePayload(payload?: string | null): string | null {
  const data = parseEventPayload(payload)
  if (!data) {
    if (!payload) return null
    return payload.length > 120 ? `${payload.slice(0, 117)}…` : payload
  }

  if (data.changeSummary) {
    return data.changeSummary
  }

  const parts: string[] = []

  const actionLabel = formatActionLabel(data.action)
  if (actionLabel) parts.push(actionLabel)
  if (data.reason) parts.push(data.reason)
  if (data.filePath) parts.push(data.filePath)
  if (data.tool) parts.push(`tool: ${data.tool}`)
  if (data.command) parts.push(data.command)
  if (typeof data.turn === 'number') parts.push(`turn ${data.turn}`)
  if (data.status) parts.push(data.status)
  if (typeof data.exitCode === 'number') parts.push(`exit ${data.exitCode}`)

  if (parts.length > 0) {
    return parts.join(' · ')
  }

  const compact = JSON.stringify(data)
  return compact.length > 120 ? `${compact.slice(0, 117)}…` : compact
}

export function eventDetailLines(payload?: string | null): string[] {
  const data = parseEventPayload(payload)
  if (!data) return []

  const lines: string[] = []

  if (data.context) {
    lines.push(`Context: ${data.context}`)
  }
  if (data.userFeedback) {
    lines.push(`User feedback: ${data.userFeedback}`)
  }
  if (data.affectedFiles && data.affectedFiles.length > 0) {
    lines.push(`Affected files: ${data.affectedFiles.join(', ')}`)
  }
  if (typeof data.editOrdinal === 'number') {
    lines.push(`Edit #${data.editOrdinal}`)
  }
  if (typeof data.linesAdded === 'number' || typeof data.linesRemoved === 'number') {
    lines.push(`Lines +${data.linesAdded ?? 0} / -${data.linesRemoved ?? 0}`)
  }
  if (data.outputPreview) {
    lines.push(`Output: ${data.outputPreview}`)
  }

  return lines
}

export function eventPreviewBlocks(payload?: string | null): { label: string; value: string }[] {
  const data = parseEventPayload(payload)
  if (!data) return []

  const blocks: { label: string; value: string }[] = []
  if (data.oldPreview) blocks.push({ label: 'Before', value: data.oldPreview })
  if (data.newPreview) blocks.push({ label: 'After', value: data.newPreview })
  if (data.patchPreview) blocks.push({ label: 'Patch', value: data.patchPreview })
  return blocks
}

export function hasExpandablePayload(payload?: string | null): boolean {
  if (!payload) return false
  const data = parseEventPayload(payload)
  if (!data) return payload.trim().length > 0

  return Boolean(
    data.oldPreview
    || data.newPreview
    || data.patchPreview
    || data.context
    || data.reason
    || data.userFeedback
    || (data.affectedFiles && data.affectedFiles.length > 0)
    || Object.keys(data).length > 0,
  )
}

export function formatPayloadJson(payload?: string | null): string | null {
  const data = parseEventPayload(payload)
  if (!data) return payload ?? null
  return JSON.stringify(data, null, 2)
}
