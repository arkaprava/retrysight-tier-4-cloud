/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { BarList, Card, Title } from '@tremor/react'
import type { MonitorTremorColor } from '../../theme/tremor/chart-colors'
import { MONITOR_TREMOR_COLORS } from '../../theme/tremor/chart-colors'

type BarListItem = {
  name: string
  value: number
  color?: MonitorTremorColor
}

type BarListPanelProps = {
  title: string
  data: BarListItem[]
  valueFormatter?: (value: number) => string
  /** Fallback when an item has no per-row color. */
  color?: MonitorTremorColor
  className?: string
  emptyMessage?: string
}

export default function BarListPanel({
  title,
  data,
  valueFormatter,
  color = MONITOR_TREMOR_COLORS[0],
  className,
  emptyMessage = 'No data for this range.',
}: BarListPanelProps) {
  const hasPerItemColors = data.some((item) => item.color)

  return (
    <Card
      className={`analytics-surface ring-1 ring-tremor-ring dark:ring-dark-tremor-ring ${className ?? ''}`}
    >
      <Title className="text-tremor-content-strong dark:text-dark-tremor-content-strong">{title}</Title>
      {data.length === 0 ? (
        <p className="mt-6 text-sm text-tremor-content dark:text-dark-tremor-content">{emptyMessage}</p>
      ) : (
        <BarList
          className="mt-4"
          data={data}
          valueFormatter={valueFormatter}
          // When rows carry their own colors, omit the shared color so Tremor uses item.color.
          color={hasPerItemColors ? undefined : color}
        />
      )}
    </Card>
  )
}
