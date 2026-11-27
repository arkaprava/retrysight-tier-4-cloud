/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import {
  AreaChart,
  BarChart,
  Card,
  LineChart,
  Title,
} from '@tremor/react'
import { useMemo, type ReactNode } from 'react'
import type { ChartWindowAlign } from '../../hooks/useChartZoom'
import { useChartZoom } from '../../hooks/useChartZoom'
import {
  MONITOR_TREMOR_COLORS,
  type MonitorTremorColor,
} from '../../theme/tremor/chart-colors'

type ChartType = 'line' | 'bar' | 'area'

type AnalyticsChartProps<T extends Record<string, unknown>> = {
  title: string
  data: T[]
  index: keyof T & string
  categories: string[]
  type?: ChartType
  colors?: MonitorTremorColor[]
  /**
   * When set (typically for single-metric bar charts), each index label becomes
   * its own series so tools/models keep a distinct, stable color.
   */
  colorForIndex?: (indexValue: string) => MonitorTremorColor
  valueFormatter?: (value: number) => string
  align?: ChartWindowAlign
  minVisible?: number
  height?: string
  emptyMessage?: string
  showZoom?: boolean
  className?: string
  header?: ReactNode
  footer?: ReactNode
}

function toPerIndexBarSeries<T extends Record<string, unknown>>(
  data: T[],
  index: keyof T & string,
  valueKey: string,
  colorForIndex: (indexValue: string) => MonitorTremorColor,
): {
  data: Array<Record<string, string | number>>
  categories: string[]
  colors: MonitorTremorColor[]
} {
  const categories = data.map((row) => String(row[index] ?? ''))
  return {
    data: data.map((row) => {
      const label = String(row[index] ?? '')
      const value = Number(row[valueKey] ?? 0)
      return { [index]: label, [label]: value }
    }),
    categories,
    colors: categories.map((label) => colorForIndex(label)),
  }
}

export default function AnalyticsChart<T extends Record<string, unknown>>({
  title,
  data,
  index,
  categories,
  type = 'line',
  colors = MONITOR_TREMOR_COLORS.slice(0, 2),
  colorForIndex,
  valueFormatter,
  align = 'center',
  minVisible,
  height = 'h-72',
  emptyMessage = 'No data for this range.',
  showZoom = true,
  className,
  header,
  footer,
}: AnalyticsChartProps<T>) {
  const perIndexMode = type === 'bar' && !!colorForIndex && categories.length === 1

  const {
    zoomedData,
    zoomIn,
    zoomOut,
    reset,
    canZoomIn,
    canZoomOut,
    canReset,
    visibleCount,
    totalCount,
  } = useChartZoom(data, { align, minVisible })

  const visibleSeries = useMemo(() => {
    if (!perIndexMode || !colorForIndex) {
      return null
    }
    return toPerIndexBarSeries(zoomedData, index, categories[0], colorForIndex)
  }, [perIndexMode, colorForIndex, zoomedData, index, categories])

  const chartCategories = visibleSeries?.categories ?? categories
  const chartColors = visibleSeries
    ? visibleSeries.colors
    : chartCategories.map(
        (_, indexInCategory) => colors[indexInCategory % colors.length] ?? MONITOR_TREMOR_COLORS[0],
      )
  const commonProps = {
    className: `mt-4 ${height}`,
    data: visibleSeries?.data ?? zoomedData,
    index,
    categories: chartCategories,
    colors: chartColors,
    valueFormatter,
    // Per-index bars already encode identity in the x-axis; a legend is noise.
    showLegend: !visibleSeries && chartCategories.length > 1,
    showGridLines: true,
    yAxisWidth: 56,
  }

  return (
    <Card
      className={`analytics-surface ring-1 ring-tremor-ring dark:ring-dark-tremor-ring ${className ?? ''}`}
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <Title className="text-tremor-content-strong dark:text-dark-tremor-content-strong">{title}</Title>
        {header}
      </div>

      {totalCount === 0 ? (
        <p className="mt-6 text-sm text-tremor-content dark:text-dark-tremor-content">{emptyMessage}</p>
      ) : (
        <>
          {showZoom && totalCount > (minVisible ?? 3) && (
            <div className="mt-3 flex flex-wrap items-center gap-2" role="toolbar" aria-label={`${title} zoom controls`}>
              <button
                type="button"
                className="rounded-tremor-small border border-tremor-border bg-tremor-background px-2 py-1 text-xs text-tremor-content hover:bg-tremor-background-subtle disabled:opacity-40 dark:border-dark-tremor-border dark:bg-dark-tremor-background dark:text-dark-tremor-content dark:hover:bg-dark-tremor-background-subtle"
                onClick={zoomIn}
                disabled={!canZoomIn}
                aria-label={`Zoom in on ${title}`}
              >
                +
              </button>
              <button
                type="button"
                className="rounded-tremor-small border border-tremor-border bg-tremor-background px-2 py-1 text-xs text-tremor-content hover:bg-tremor-background-subtle disabled:opacity-40 dark:border-dark-tremor-border dark:bg-dark-tremor-background dark:text-dark-tremor-content dark:hover:bg-dark-tremor-background-subtle"
                onClick={zoomOut}
                disabled={!canZoomOut}
                aria-label={`Zoom out on ${title}`}
              >
                −
              </button>
              <button
                type="button"
                className="rounded-tremor-small border border-tremor-border bg-tremor-background px-2 py-1 text-xs text-tremor-content hover:bg-tremor-background-subtle disabled:opacity-40 dark:border-dark-tremor-border dark:bg-dark-tremor-background dark:text-dark-tremor-content dark:hover:bg-dark-tremor-background-subtle"
                onClick={reset}
                disabled={!canReset}
                aria-label={`Reset zoom on ${title}`}
              >
                Reset
              </button>
              {canReset && (
                <span className="text-xs text-tremor-content-subtle dark:text-dark-tremor-content-subtle">
                  {visibleCount} of {totalCount}
                </span>
              )}
            </div>
          )}

          {type === 'bar' && <BarChart {...commonProps} />}
          {type === 'area' && <AreaChart {...commonProps} />}
          {type === 'line' && <LineChart {...commonProps} />}
        </>
      )}

      {footer}
    </Card>
  )
}
