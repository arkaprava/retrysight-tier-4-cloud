/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useMemo, useState } from 'react'
import AnalyticsChart from './AnalyticsChart'
import {
  buildTrendChartView,
  type TimeSeriesPoint,
  type TrendBreakdownMode,
  type TrendSeries,
} from '../../utils/trendSeries'

type TrendBreakdownChartProps = {
  title: string
  total: TimeSeriesPoint[]
  byTool?: TrendSeries[]
  byModel?: TrendSeries[]
  valueFormatter?: (value: number) => string
  defaultMode?: TrendBreakdownMode
  className?: string
}

const MODES: Array<{ id: TrendBreakdownMode; label: string }> = [
  { id: 'total', label: 'Total' },
  { id: 'tool', label: 'By Tool' },
  { id: 'model', label: 'By LLM' },
]

export default function TrendBreakdownChart({
  title,
  total,
  byTool = [],
  byModel = [],
  valueFormatter,
  defaultMode = 'total',
  className,
}: TrendBreakdownChartProps) {
  const [mode, setMode] = useState<TrendBreakdownMode>(defaultMode)

  const view = useMemo(
    () => buildTrendChartView(mode, total, byTool, byModel),
    [mode, total, byTool, byModel],
  )

  return (
    <AnalyticsChart
      title={title}
      type="line"
      data={view.data}
      index="timestamp"
      categories={view.categories}
      colors={view.colors}
      valueFormatter={valueFormatter}
      align="end"
      className={className}
      header={
        <div className="trend-breakdown-toggle" role="group" aria-label={`${title} breakdown`}>
          {MODES.map((entry) => (
            <button
              key={entry.id}
              type="button"
              className={mode === entry.id ? 'is-active' : undefined}
              aria-pressed={mode === entry.id}
              onClick={() => setMode(entry.id)}
            >
              {entry.label}
            </button>
          ))}
        </div>
      }
    />
  )
}
