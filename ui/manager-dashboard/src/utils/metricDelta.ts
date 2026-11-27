/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

export type MetricSentiment = 'positive' | 'negative' | 'neutral'

export type MetricDelta = {
  /** Percent change vs prior period; null when prior was zero and current is non-zero. */
  percent: number | null
  label: string
  sentiment: MetricSentiment
}

const MS_PER_DAY = 86_400_000

/** Rolling 7-day window ending at `anchor`, plus the prior 7-day window. */
export function rollingWeekRanges(anchorMs = Date.now()) {
  const to = anchorMs
  const from = to - 7 * MS_PER_DAY
  const prevTo = from
  const prevFrom = from - 7 * MS_PER_DAY
  return {
    current: {
      from: new Date(from).toISOString(),
      to: new Date(to).toISOString(),
    },
    previous: {
      from: new Date(prevFrom).toISOString(),
      to: new Date(prevTo).toISOString(),
    },
  }
}

export function computePercentChange(current: number, previous: number): number | null {
  if (!Number.isFinite(current) || !Number.isFinite(previous)) {
    return null
  }
  if (previous === 0) {
    if (current === 0) {
      return 0
    }
    return null
  }
  return ((current - previous) / Math.abs(previous)) * 100
}

/** Rounds large swings to whole percents; smaller moves keep one decimal. */
export function formatPercentChange(percent: number | null, approximate = true): string {
  if (percent === null) {
    return 'New this week'
  }
  if (percent === 0) {
    return 'No change'
  }
  const abs = Math.abs(percent)
  const rounded = approximate
    ? abs >= 10
      ? Math.round(percent)
      : Math.round(percent * 10) / 10
    : Math.round(percent * 10) / 10
  const prefix = approximate && abs >= 10 ? '~' : ''
  const sign = rounded > 0 ? '+' : ''
  return `${prefix}${sign}${rounded}%`
}

export function deltaSentiment(
  percent: number | null,
  lowerIsBetter: boolean,
): MetricSentiment {
  if (percent === null || percent === 0) {
    return 'neutral'
  }
  const improved = lowerIsBetter ? percent < 0 : percent > 0
  return improved ? 'positive' : 'negative'
}

export function buildMetricDelta(
  current: number,
  previous: number,
  lowerIsBetter = false,
  label = 'vs last week',
): MetricDelta {
  const percent = computePercentChange(current, previous)
  return {
    percent,
    label,
    sentiment: deltaSentiment(percent, lowerIsBetter),
  }
}
