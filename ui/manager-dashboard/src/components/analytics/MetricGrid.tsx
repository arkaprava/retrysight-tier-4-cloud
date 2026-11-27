/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import type { ReactNode } from 'react'

type MetricGridProps = {
  children: ReactNode
  className?: string
}

export default function MetricGrid({ children, className }: MetricGridProps) {
  return (
    <div className={`analytics-metric-grid ${className ?? ''}`}>
      {children}
    </div>
  )
}
