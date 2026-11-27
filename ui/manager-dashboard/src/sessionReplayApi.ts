/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

export type SessionReplayFormat = 'JSON' | 'MARKDOWN' | 'PROMPT'

export async function fetchTaskSessionReplay(
  taskId: string,
  token: string,
  format: SessionReplayFormat = 'PROMPT',
): Promise<string> {
  const response = await fetch(`/api/v1/tasks/${taskId}/replay?format=${format}`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
    redirect: 'manual',
  })
  if (response.type === 'opaqueredirect' || response.status === 301 || response.status === 302 || response.status === 303) {
    throw new Error('Session replay auth failed (redirected). Sign in again and retry.')
  }
  if (!response.ok) {
    const detail = await response.text().catch(() => '')
    throw new Error(detail?.trim() || `Failed to load session replay (${response.status})`)
  }
  if (format === 'JSON') {
    const data = await response.json()
    return JSON.stringify(data, null, 2)
  }
  const text = await response.text()
  if (text.trimStart().startsWith('<!DOCTYPE') || text.trimStart().startsWith('<html')) {
    throw new Error('Session replay returned HTML instead of replay text. Check manager auth config.')
  }
  return text
}
