/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { pageNumbers } from '../utils/pagination'

type TaskTablePaginationProps = {
  page: number
  pageSize: number
  totalCount: number
  loading?: boolean
  onPageChange: (page: number) => void
}

export function TaskTablePagination({
  page,
  pageSize,
  totalCount,
  loading = false,
  onPageChange,
}: TaskTablePaginationProps) {
  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize))
  const rangeStart = totalCount === 0 ? 0 : (page - 1) * pageSize + 1
  const rangeEnd = Math.min(page * pageSize, totalCount)
  const pages = pageNumbers(page, totalPages)

  return (
    <div className="pagination">
      <span className="pagination-info">
        {rangeStart}–{rangeEnd} of {totalCount}
      </span>
      <div className="pagination-controls">
        <button
          type="button"
          className="pagination-btn"
          disabled={page <= 1 || loading}
          onClick={() => onPageChange(Math.max(1, page - 1))}
        >
          Previous
        </button>
        <div className="pagination-pages" role="navigation" aria-label="Task pages">
          {pages.map((pageNum, index) => {
            const prev = pages[index - 1]
            const showEllipsis = prev !== undefined && pageNum - prev > 1

            return (
              <span key={pageNum} className="pagination-page-group">
                {showEllipsis && <span className="pagination-ellipsis" aria-hidden="true">…</span>}
                <button
                  type="button"
                  className={`pagination-btn pagination-page${pageNum === page ? ' active' : ''}`}
                  disabled={loading}
                  aria-current={pageNum === page ? 'page' : undefined}
                  onClick={() => onPageChange(pageNum)}
                >
                  {pageNum}
                </button>
              </span>
            )
          })}
        </div>
        <button
          type="button"
          className="pagination-btn"
          disabled={page >= totalPages || loading}
          onClick={() => onPageChange(Math.min(totalPages, page + 1))}
        >
          Next
        </button>
      </div>
    </div>
  )
}
