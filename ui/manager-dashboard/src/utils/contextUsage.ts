/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import type { TaskTimelineEvent } from './taskEvents'

export type TokenUsageSummary = {
  inputTokens: number
  outputTokens: number
  cacheReadTokens: number
  cacheWriteTokens: number
  reasoningTokens: number
  estimatedCostUsd: number
}

export type TokenTurn = {
  turnIndex: number
  model?: string | null
  inputTokens: number
  outputTokens: number
  cacheReadTokens?: number
  cacheWriteTokens?: number
  reasoningTokens?: number
  estimatedCostUsd?: number | null
  recordedAt?: string | null
}

export type CompactionEvent = {
  id: string
  occurredAt: string
  sequenceNum: number
  nearestTurn?: number
}

export type ContextUsageChartPoint = {
  turn: number
  input: number
  output: number
  cacheRead: number
  cacheWrite: number
  reasoning: number
  contextUsed: number
  total: number
  isCompaction: boolean
  fillPercent?: number
}

const METADATA_LABELS: Record<string, string> = {
  collector: 'Collector',
  model: 'Model',
  contextWindow: 'Context window',
  conversationId: 'Conversation ID',
  subagent: 'Subagent',
  projectRoot: 'Project root',
  repoName: 'Repository',
  gitBranch: 'Git branch',
  gitCommit: 'Git commit',
  gitCommitShort: 'Commit (short)',
  remoteUrl: 'Remote URL',
  remoteHost: 'Remote host',
  isDirty: 'Dirty working tree',
  resolvedFrom: 'Resolved from',
  isSubmodule: 'Submodule',
}

export function parseTaskMetadata(metadata?: string | null): Record<string, unknown> | null {
  if (!metadata) return null
  try {
    return JSON.parse(metadata) as Record<string, unknown>
  } catch {
    return null
  }
}

export function formatTokenCount(value: number): string {
  return value.toLocaleString()
}

export function formatUsd(value?: number | null): string | null {
  if (value == null || value <= 0) return null
  return `$${value.toFixed(4)}`
}

export function sumTokenCategories(usage: TokenUsageSummary): number {
  return (
    usage.inputTokens
    + usage.outputTokens
    + usage.cacheReadTokens
    + usage.cacheWriteTokens
    + usage.reasoningTokens
  )
}

export function turnContextTokens(turn: TokenTurn): number {
  return (
    turn.inputTokens
    + (turn.cacheReadTokens ?? 0)
    + (turn.cacheWriteTokens ?? 0)
    + (turn.reasoningTokens ?? 0)
  )
}

export function turnTotalTokens(turn: TokenTurn): number {
  return turnContextTokens(turn) + turn.outputTokens
}

export function resolveContextWindow(metadata: Record<string, unknown> | null): number | null {
  if (!metadata) return null
  const raw = metadata.contextWindow ?? metadata.context_window
  if (typeof raw === 'number' && raw > 0) return raw
  if (typeof raw === 'string') {
    const parsed = Number.parseInt(raw, 10)
    if (!Number.isNaN(parsed) && parsed > 0) return parsed
  }
  return null
}

export function resolveModel(
  metadata: Record<string, unknown> | null,
  tokenTurns: TokenTurn[],
): string | null {
  const fromMetadata = metadata?.model
  if (typeof fromMetadata === 'string' && fromMetadata.trim()) {
    return fromMetadata.trim()
  }

  for (let index = tokenTurns.length - 1; index >= 0; index -= 1) {
    const model = tokenTurns[index].model?.trim()
    if (model) return model
  }

  return null
}

export function computePeakContextTokens(tokenTurns: TokenTurn[]): number {
  if (tokenTurns.length === 0) return 0
  return Math.max(...tokenTurns.map(turnContextTokens))
}

export function computeLatestContextTokens(tokenTurns: TokenTurn[]): number {
  if (tokenTurns.length === 0) return 0
  const sorted = [...tokenTurns].sort((a, b) => a.turnIndex - b.turnIndex)
  return turnContextTokens(sorted[sorted.length - 1])
}

export function computeContextFillPercent(used: number, limit: number | null): number | null {
  if (!limit || limit <= 0 || used <= 0) return null
  return Math.min(100, (used / limit) * 100)
}

export function formatFillPercent(percent: number | null): string {
  if (percent == null) return '—'
  return `${percent.toFixed(1)}%`
}

export function extractCompactionEvents(events: TaskTimelineEvent[]): CompactionEvent[] {
  return events
    .filter((event) => event.eventType === 'COMPACTION')
    .map((event) => ({
      id: event.id,
      occurredAt: event.occurredAt,
      sequenceNum: event.sequenceNum,
    }))
}

function nearestTurnForTime(occurredAt: string, tokenTurns: TokenTurn[]): number | undefined {
  const target = new Date(occurredAt).getTime()
  if (Number.isNaN(target) || tokenTurns.length === 0) return undefined

  let bestTurn: number | undefined
  let bestDelta = Number.POSITIVE_INFINITY

  for (const turn of tokenTurns) {
    if (!turn.recordedAt) continue
    const recorded = new Date(turn.recordedAt).getTime()
    if (Number.isNaN(recorded)) continue
    const delta = Math.abs(recorded - target)
    if (delta < bestDelta) {
      bestDelta = delta
      bestTurn = turn.turnIndex
    }
  }

  return bestDelta <= 120_000 ? bestTurn : undefined
}

export function enrichCompactionEvents(
  compactions: CompactionEvent[],
  tokenTurns: TokenTurn[],
): CompactionEvent[] {
  return compactions.map((event) => ({
    ...event,
    nearestTurn: nearestTurnForTime(event.occurredAt, tokenTurns),
  }))
}

export function buildContextChartData(
  tokenTurns: TokenTurn[],
  compactionTurns: Set<number>,
  contextWindow: number | null,
): ContextUsageChartPoint[] {
  return [...tokenTurns]
    .sort((a, b) => a.turnIndex - b.turnIndex)
    .map((turn) => {
      const cacheRead = turn.cacheReadTokens ?? 0
      const cacheWrite = turn.cacheWriteTokens ?? 0
      const reasoning = turn.reasoningTokens ?? 0
      const contextUsed = turn.inputTokens + cacheRead + cacheWrite + reasoning
      const fillPercent = computeContextFillPercent(contextUsed, contextWindow) ?? undefined

      return {
        turn: turn.turnIndex,
        input: turn.inputTokens,
        output: turn.outputTokens,
        cacheRead,
        cacheWrite,
        reasoning,
        contextUsed,
        total: contextUsed + turn.outputTokens,
        isCompaction: compactionTurns.has(turn.turnIndex),
        fillPercent,
      }
    })
}

export function formatMetadataValue(value: unknown): string {
  if (value == null) return '—'
  if (typeof value === 'boolean') return value ? 'Yes' : 'No'
  if (typeof value === 'number') return value.toLocaleString()
  if (typeof value === 'string') return value
  if (Array.isArray(value)) return value.map((item) => formatMetadataValue(item)).join(', ')
  return JSON.stringify(value)
}

export function structuredMetadataEntries(
  metadata: Record<string, unknown> | null,
): { key: string; label: string; value: string }[] {
  if (!metadata) return []

  const knownKeys = Object.keys(METADATA_LABELS)
  const entries: { key: string; label: string; value: string }[] = []

  for (const key of knownKeys) {
    if (key in metadata) {
      entries.push({
        key,
        label: METADATA_LABELS[key],
        value: formatMetadataValue(metadata[key]),
      })
    }
  }

  for (const [key, value] of Object.entries(metadata)) {
    if (knownKeys.includes(key)) continue
    entries.push({
      key,
      label: key.replace(/([A-Z])/g, ' $1').replace(/^./, (char) => char.toUpperCase()),
      value: formatMetadataValue(value),
    })
  }

  return entries
}

export function hasContextUsageData(
  tokenUsage: TokenUsageSummary,
  tokenTurns: TokenTurn[],
  metadata: Record<string, unknown> | null,
  compactions: CompactionEvent[],
): boolean {
  return (
    tokenTurns.length > 0
    || sumTokenCategories(tokenUsage) > 0
    || resolveContextWindow(metadata) != null
    || structuredMetadataEntries(metadata).length > 0
    || compactions.length > 0
  )
}
