/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

export function formatTaskStartedAt(startedAt?: string | null): string {
  if (!startedAt) return '—'

  const date = new Date(startedAt)
  if (Number.isNaN(date.getTime())) {
    return startedAt
  }

  return date.toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}
