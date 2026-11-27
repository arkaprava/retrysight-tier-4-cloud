/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useState } from 'react'
import { defaultDateRange } from '../reportApi'

type DateRangeFilterProps = {
  fromDate: string
  toDate: string
  onFromChange: (value: string) => void
  onToChange: (value: string) => void
}

export function useDefaultDateRange() {
  const initial = defaultDateRange(90)
  return useState({ fromDate: initial.from, toDate: initial.to })
}

export function DateRangeFilter({ fromDate, toDate, onFromChange, onToChange }: DateRangeFilterProps) {
  return (
    <div className="cost-date-range">
      <label>
        <span>From</span>
        <input type="date" value={fromDate} onChange={(event) => onFromChange(event.target.value)} />
      </label>
      <label>
        <span>To</span>
        <input type="date" value={toDate} onChange={(event) => onToChange(event.target.value)} />
      </label>
    </div>
  )
}
