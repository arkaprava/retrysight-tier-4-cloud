/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { Card, Table, TableBody, TableCell, TableHead, TableHeaderCell, TableRow, Title } from '@tremor/react'
import { useMemo } from 'react'
import { formatSourceTool } from '../../sourceTools'
import { formatUsd } from '../../utils/contextUsage'

export type LlmRunMetric = {
  sourceTool: string
  model: string
  turnCount: number
  costUsd: number
}

type LlmRunsByIdePanelProps = {
  title?: string
  runs: LlmRunMetric[]
  showCost?: boolean
  className?: string
  emptyMessage?: string
}

type IdeGroup = {
  sourceTool: string
  label: string
  totalTurns: number
  totalCost: number
  models: LlmRunMetric[]
}

export default function LlmRunsByIdePanel({
  title = 'LLM runs by IDE',
  runs,
  showCost = true,
  className,
  emptyMessage = 'No LLM turn data recorded yet.',
}: LlmRunsByIdePanelProps) {
  const groups = useMemo(() => {
    const byIde = new Map<string, IdeGroup>()
    for (const run of runs) {
      const key = run.sourceTool
      const existing = byIde.get(key)
      if (existing) {
        existing.totalTurns += run.turnCount
        existing.totalCost += run.costUsd
        existing.models.push(run)
      } else {
        byIde.set(key, {
          sourceTool: key,
          label: formatSourceTool(key),
          totalTurns: run.turnCount,
          totalCost: run.costUsd,
          models: [run],
        })
      }
    }
    return [...byIde.values()]
      .map((group) => ({
        ...group,
        models: [...group.models].sort((a, b) => b.turnCount - a.turnCount),
      }))
      .sort((a, b) => b.totalTurns - a.totalTurns)
  }, [runs])

  return (
    <Card
      className={`analytics-surface ring-1 ring-tremor-ring dark:ring-dark-tremor-ring ${className ?? ''}`}
    >
      <Title className="text-tremor-content-strong dark:text-dark-tremor-content-strong">{title}</Title>
      <p className="mt-1 text-sm text-tremor-content dark:text-dark-tremor-content">
        Each IDE can use different LLM models per turn — grouped by collector and underlying model.
      </p>

      {groups.length === 0 ? (
        <p className="mt-6 text-sm text-tremor-content dark:text-dark-tremor-content">{emptyMessage}</p>
      ) : (
        <div className="mt-4 space-y-5">
          {groups.map((group) => (
            <div key={group.sourceTool} className="rounded-tremor-default border border-tremor-border p-4 dark:border-dark-tremor-border">
              <div className="mb-3 flex flex-wrap items-end justify-between gap-3">
                <div>
                  <h4 className="text-base font-semibold text-tremor-content-strong dark:text-dark-tremor-content-strong">
                    {group.label}
                  </h4>
                  <p className="text-sm text-tremor-content dark:text-dark-tremor-content">
                    {group.totalTurns.toLocaleString()} LLM turns
                    {showCost && ` · ${formatUsd(group.totalCost)}`}
                  </p>
                </div>
                <span className="rounded-full bg-tremor-background-subtle px-2.5 py-1 text-xs font-medium text-tremor-content dark:bg-dark-tremor-background-subtle dark:text-dark-tremor-content">
                  {group.models.length} model{group.models.length === 1 ? '' : 's'}
                </span>
              </div>

              <Table>
                <TableHead>
                  <TableRow>
                    <TableHeaderCell>Model</TableHeaderCell>
                    <TableHeaderCell className="text-right">Turns</TableHeaderCell>
                    {showCost && <TableHeaderCell className="text-right">Cost</TableHeaderCell>}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {group.models.map((run) => (
                    <TableRow key={`${group.sourceTool}-${run.model}`}>
                      <TableCell>
                        <span className="font-medium text-tremor-content-strong dark:text-dark-tremor-content-strong">
                          {run.model}
                        </span>
                      </TableCell>
                      <TableCell className="text-right font-mono tabular-nums">
                        {run.turnCount.toLocaleString()}
                      </TableCell>
                      {showCost && (
                        <TableCell className="text-right font-mono tabular-nums">
                          {formatUsd(run.costUsd)}
                        </TableCell>
                      )}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          ))}
        </div>
      )}
    </Card>
  )
}
