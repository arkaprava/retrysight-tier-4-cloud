/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

type ToolMetric = { value: number }
type LlmRun = { turnCount: number }
type UserCost = { costUsd?: number }

export function sumToolMetrics(metrics: ToolMetric[] | undefined | null): number {
  return (metrics ?? []).reduce((sum, entry) => sum + entry.value, 0)
}

export function countActiveTools(metrics: ToolMetric[] | undefined | null): number {
  return (metrics ?? []).filter((entry) => entry.value > 0).length
}

export function sumLlmTurns(runs: LlmRun[] | undefined | null): number {
  return (runs ?? []).reduce((sum, run) => sum + run.turnCount, 0)
}

export function countActiveDevelopers(users: UserCost[] | undefined | null): number {
  return (users ?? []).filter((user) => (user.costUsd ?? 0) > 0).length
}

export function avgCostPerTask(totalCostUsd: number, totalTasks: number): number {
  if (totalTasks <= 0) {
    return 0
  }
  return totalCostUsd / totalTasks
}

export function countLiveAgents(agents: Array<{ stale?: boolean }> | undefined | null): {
  active: number
  stale: number
  total: number
} {
  const list = agents ?? []
  const stale = list.filter((agent) => agent.stale).length
  return {
    active: list.length - stale,
    stale,
    total: list.length,
  }
}
