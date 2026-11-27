/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { fetchTaskSessionReplay, type SessionReplayFormat } from '../sessionReplayApi'
import SessionWaterfall from './SessionWaterfall'
import type { SpanNode } from '../utils/sessionSpans'
import type { TaskTimelineEvent } from '../utils/taskEvents'

type TaskSessionReplayPanelProps = {
  taskId: string
  spans?: SpanNode[] | null
  events?: TaskTimelineEvent[] | null
}

export default function TaskSessionReplayPanel({
  taskId,
  spans,
  events,
}: TaskSessionReplayPanelProps) {
  const { token } = useAuth()
  const [format, setFormat] = useState<SessionReplayFormat>('PROMPT')
  const [preview, setPreview] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  async function loadReplay(selectedFormat: SessionReplayFormat = format) {
    if (!token) return
    setLoading(true)
    setError(null)
    setCopied(false)
    try {
      const replay = await fetchTaskSessionReplay(taskId, token, selectedFormat)
      setPreview(replay)
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Failed to load replay')
    } finally {
      setLoading(false)
    }
  }

  async function copyReplay() {
    if (!preview) {
      await loadReplay()
    }
    const text = preview ?? (token ? await fetchTaskSessionReplay(taskId, token, format) : '')
    if (!text) return
    await navigator.clipboard.writeText(text)
    setCopied(true)
  }

  return (
    <div className="card section-gap session-replay-panel">
      <div className="session-replay-header">
        <div>
          <h3>Session replay for AI agent</h3>
          <p className="page-subtitle">
            Export a structured replay of this task for another coding agent to continue or analyze the session.
          </p>
        </div>
        <div className="session-replay-actions">
          <label className="session-replay-format">
            Format
            <select
              value={format}
              onChange={(e) => setFormat(e.target.value as SessionReplayFormat)}
            >
              <option value="PROMPT">Prompt</option>
              <option value="MARKDOWN">Markdown</option>
              <option value="JSON">JSON</option>
            </select>
          </label>
          <button type="button" onClick={() => void loadReplay()} disabled={loading}>
            {loading ? 'Loading…' : 'Preview replay'}
          </button>
          <button type="button" onClick={() => void copyReplay()} disabled={loading}>
            {copied ? 'Copied' : 'Copy for AI agent'}
          </button>
        </div>
      </div>

      <SessionWaterfall spans={spans} events={events} embedded />

      {error && <p className="login-error">{error}</p>}

      {preview && (
        <pre className="session-replay-preview">{preview}</pre>
      )}
    </div>
  )
}
