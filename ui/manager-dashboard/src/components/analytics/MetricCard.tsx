/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { Card, Metric, Text } from '@tremor/react'
import type { ReactNode } from 'react'
import { formatPercentChange, type MetricDelta } from '../../utils/metricDelta'

type MetricCardProps = {
  title: string
  value: ReactNode
  subtitle?: string
  delta?: MetricDelta
  variant?: 'default' | 'alert'
  live?: boolean
  className?: string
}

function deltaArrow(percent: number | null): string {
  if (percent === null) {
    return '●'
  }
  if (percent > 0) {
    return '↑'
  }
  if (percent < 0) {
    return '↓'
  }
  return '→'
}

export default function MetricCard({
  title,
  value,
  subtitle,
  delta,
  variant = 'default',
  live = false,
  className,
}: MetricCardProps) {
  const accentClass =
    variant === 'alert'
      ? 'metric-card-alert'
      : 'metric-card-default'

  return (
    <Card
      className={`analytics-surface metric-card ring-1 ring-tremor-ring dark:ring-dark-tremor-ring ${accentClass} ${className ?? ''}`}
    >
      {live && (
        <span className="metric-live-badge" aria-label="Live updating">
          <span className="metric-live-dot" aria-hidden="true" />
          Live
        </span>
      )}

      <div className="metric-card-body">
        <div className="metric-card-header">
          <Text className="metric-card-title text-tremor-content dark:text-dark-tremor-content">
            {title}
          </Text>
        </div>

        <Metric className="metric-card-value mt-1 font-mono tabular-nums text-tremor-content-strong dark:text-dark-tremor-content-strong">
          {value}
        </Metric>

        {delta && (
          <p
            className={`metric-delta metric-delta-${delta.sentiment}`}
            title="Compared to the previous 7-day period"
          >
            <span className="metric-delta-main">
              <span className="metric-delta-arrow" aria-hidden="true">
                {deltaArrow(delta.percent)}
              </span>
              <span>{formatPercentChange(delta.percent)}</span>
            </span>
            <span className="metric-delta-label">{delta.label}</span>
          </p>
        )}

        {subtitle && (
          <Text className="metric-card-subtitle mt-1 text-tremor-content-subtle dark:text-dark-tremor-content-subtle">
            {subtitle}
          </Text>
        )}
      </div>
    </Card>
  )
}
