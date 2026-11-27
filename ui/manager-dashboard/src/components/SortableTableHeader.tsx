/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import type { SortDirection } from '../hooks/useTableSort'

type SortableTableHeaderProps = {
  label: string
  columnKey: string
  sortKey: string | null
  sortDirection: SortDirection
  onSort: (key: string) => void
}

export function SortableTableHeader({
  label,
  columnKey,
  sortKey,
  sortDirection,
  onSort,
}: SortableTableHeaderProps) {
  const isActive = sortKey === columnKey
  const sortIcon = !isActive ? '↕' : sortDirection === 'asc' ? '↑' : '↓'

  return (
    <th scope="col">
      <button
        type="button"
        className={`table-sort-btn${isActive ? ' active' : ''}`}
        onClick={() => onSort(columnKey)}
        aria-sort={
          isActive ? (sortDirection === 'asc' ? 'ascending' : 'descending') : 'none'
        }
      >
        <span className="table-sort-label">{label}</span>
        <span className="table-sort-icon" aria-hidden="true">
          {sortIcon}
        </span>
      </button>
    </th>
  )
}
