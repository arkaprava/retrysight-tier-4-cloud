/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useCallback, useMemo, useState } from 'react'

export type SortDirection = 'asc' | 'desc'
export type SortValueType = 'text' | 'number' | 'date' | 'status'

export type SortColumn<T> = {
  key: string
  type: SortValueType
  getValue: (row: T) => unknown
}

type SortState = {
  key: string | null
  direction: SortDirection
}

const STATUS_ORDER = [
  'PENDING',
  'QUEUED',
  'RUNNING',
  'ACTIVE',
  'COMPLETED',
  'SUCCESS',
  'FAILED',
  'ERROR',
]

function normalizeText(value: unknown): string {
  if (value == null) return ''
  return String(value).trim()
}

function compareValues(a: unknown, b: unknown, type: SortValueType): number {
  if (type === 'number') {
    const na = typeof a === 'number' && Number.isFinite(a) ? a : Number(a ?? 0)
    const nb = typeof b === 'number' && Number.isFinite(b) ? b : Number(b ?? 0)
    return na - nb
  }

  if (type === 'date') {
    const ta = a ? new Date(String(a)).getTime() : 0
    const tb = b ? new Date(String(b)).getTime() : 0
    const safeTa = Number.isFinite(ta) ? ta : 0
    const safeTb = Number.isFinite(tb) ? tb : 0
    return safeTa - safeTb
  }

  if (type === 'status') {
    const sa = normalizeText(a).toUpperCase()
    const sb = normalizeText(b).toUpperCase()
    const ia = STATUS_ORDER.indexOf(sa)
    const ib = STATUS_ORDER.indexOf(sb)
    if (ia !== -1 && ib !== -1) return ia - ib
    if (ia !== -1) return -1
    if (ib !== -1) return 1
    return sa.localeCompare(sb, undefined, { sensitivity: 'base' })
  }

  return normalizeText(a).localeCompare(normalizeText(b), undefined, { sensitivity: 'base' })
}

export function useTableSort<T>(data: T[], columns: SortColumn<T>[]) {
  const [sortState, setSortState] = useState<SortState>({ key: null, direction: 'asc' })

  const toggleSort = useCallback((key: string) => {
    setSortState((prev) => {
      if (prev.key !== key) {
        return { key, direction: 'asc' }
      }
      if (prev.direction === 'asc') {
        return { key, direction: 'desc' }
      }
      return { key: null, direction: 'asc' }
    })
  }, [])

  const sortedData = useMemo(() => {
    if (!sortState.key) return data

    const column = columns.find((col) => col.key === sortState.key)
    if (!column) return data

    const direction = sortState.direction === 'asc' ? 1 : -1

    return [...data].sort((rowA, rowB) => {
      const valueA = column.getValue(rowA)
      const valueB = column.getValue(rowB)
      return compareValues(valueA, valueB, column.type) * direction
    })
  }, [columns, data, sortState.direction, sortState.key])

  return {
    sortedData,
    sortKey: sortState.key,
    sortDirection: sortState.direction,
    toggleSort,
  }
}
