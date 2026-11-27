/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { Card, Text, Tracker } from '@tremor/react'
import type { MonitorTremorColor } from '../../theme/tremor/chart-colors'

export type TrackerDatum = {
  key: string
  color: MonitorTremorColor
  tooltip?: string
}

type TrackerRowProps = {
  label: string
  data: TrackerDatum[]
  className?: string
}

export default function TrackerRow({ label, data, className }: TrackerRowProps) {
  if (data.length === 0) {
    return null
  }

  return (
    <Card
      className={`analytics-surface ring-1 ring-tremor-ring dark:ring-dark-tremor-ring ${className ?? ''}`}
    >
      <Text className="text-tremor-content dark:text-dark-tremor-content">{label}</Text>
      <Tracker className="mt-3" data={data} />
    </Card>
  )
}
