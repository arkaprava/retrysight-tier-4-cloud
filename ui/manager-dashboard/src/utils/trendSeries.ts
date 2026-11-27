/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { formatSourceTool } from '../sourceTools'
import {
  colorForModel,
  colorForSourceTool,
  type MonitorTremorColor,
} from '../theme/tremor/chart-colors'

export type TrendBreakdownMode = 'total' | 'tool' | 'model'

export type TimeSeriesPoint = {
  timestamp: string
  value: number
}

export type TrendSeries = {
  name: string
  points: TimeSeriesPoint[]
}

export type TrendChartView = {
  data: Array<Record<string, string | number>>
  categories: string[]
  colors: MonitorTremorColor[]
}

function formatDayLabel(timestamp: string): string {
  const day = timestamp.slice(0, 10)
  return day || timestamp
}

function colorForSeries(name: string, mode: TrendBreakdownMode): MonitorTremorColor {
  if (mode === 'tool') {
    return colorForSourceTool(name)
  }
  return colorForModel(name)
}

export function buildTrendChartView(
  mode: TrendBreakdownMode,
  total: TimeSeriesPoint[] | undefined,
  byTool: TrendSeries[] | undefined,
  byModel: TrendSeries[] | undefined,
  options?: { formatToolNames?: boolean },
): TrendChartView {
  if (mode === 'total') {
    return {
      data: (total ?? []).map((point) => ({
        timestamp: formatDayLabel(point.timestamp),
        value: point.value,
      })),
      categories: ['value'],
      colors: ['indigo'],
    }
  }

  const series = mode === 'tool' ? (byTool ?? []) : (byModel ?? [])
  const formatToolNames = options?.formatToolNames ?? mode === 'tool'
  const categories = series.map((entry) =>
    formatToolNames ? formatSourceTool(entry.name) : entry.name,
  )

  const byTimestamp = new Map<string, Record<string, string | number>>()
  for (const entry of series) {
    const category = formatToolNames ? formatSourceTool(entry.name) : entry.name
    for (const point of entry.points) {
      const key = formatDayLabel(point.timestamp)
      const row = byTimestamp.get(key) ?? { timestamp: key }
      row[category] = point.value
      byTimestamp.set(key, row)
    }
  }

  const data = [...byTimestamp.values()].sort((a, b) =>
    String(a.timestamp).localeCompare(String(b.timestamp)),
  )

  // Ensure every category exists on every row so Tremor draws continuous lines.
  for (const row of data) {
    for (const category of categories) {
      if (row[category] == null) {
        row[category] = 0
      }
    }
  }

  return {
    data,
    categories,
    colors: series.map((entry) => colorForSeries(entry.name, mode)),
  }
}
