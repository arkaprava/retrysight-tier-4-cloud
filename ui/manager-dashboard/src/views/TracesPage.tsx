/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useQuery } from '@apollo/client'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { TRACE_SPANS } from '../graphql/client'
import { usePlatformFeatures } from '../platformFeatures'

function shortId(id?: string | null): string {
  if (!id || id.length < 8) return id ?? '—'
  return `${id.slice(0, 8)}…`
}

export default function TracesPage() {
  const { traceQueryEnabled, featuresLoading } = usePlatformFeatures()
  const [taskId, setTaskId] = useState('')
  const [searchTaskId, setSearchTaskId] = useState<string | null>(null)

  const { data, loading, error, refetch } = useQuery(TRACE_SPANS, {
    variables: { taskId: searchTaskId },
    skip: !traceQueryEnabled,
    fetchPolicy: 'cache-and-network',
  })

  if (featuresLoading) {
    return (
      <div className="loading-state">
        <div className="loading-spinner" aria-hidden="true" />
        <span>Loading…</span>
      </div>
    )
  }

  if (!traceQueryEnabled) {
    return <p className="empty">Trace query requires Tier 3+ integration tier.</p>
  }

  const spans = data?.traceSpans ?? []

  function handleSearch(e: FormEvent) {
    e.preventDefault()
    const trimmed = taskId.trim()
    const next = trimmed.length > 0 ? trimmed : null
    setSearchTaskId(next)
    void refetch({ taskId: next })
  }

  return (
    <div className="analytics-surface">
      <header className="page-header">
        <h2 className="page-title">Traces</h2>
        <p className="page-subtitle">Search OTel spans ingested via /api/v1/otel/v1/traces.</p>
      </header>

      <div className="card section-gap">
        <form className="inline-form" onSubmit={handleSearch}>
          <input
            type="text"
            placeholder="Filter by task ID (optional)"
            value={taskId}
            onChange={(e) => setTaskId(e.target.value)}
          />
          <button type="submit" className="btn-primary">Search</button>
        </form>
      </div>

      <div className="card section-gap">
        {loading ? (
          <p className="empty">Loading spans…</p>
        ) : error ? (
          <p className="empty">Failed to load traces: {error.message}</p>
        ) : spans.length === 0 ? (
          <p className="empty">No spans found. Ingest traces via OTLP HTTP JSON at /api/v1/otel/v1/traces.</p>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Task</th>
                  <th>Kind</th>
                  <th>Duration</th>
                  <th>Status</th>
                  <th>Start</th>
                </tr>
              </thead>
              <tbody>
                {spans.map((span: {
                  id: string
                  name: string
                  taskId: string
                  kind?: string | null
                  durationMs: number
                  status?: string | null
                  startAt: string
                }) => (
                  <tr key={span.id}>
                    <td>{span.name}</td>
                    <td>
                      {span.taskId ? (
                        <Link to={`/tasks/${span.taskId}`}>{shortId(span.taskId)}</Link>
                      ) : (
                        '—'
                      )}
                    </td>
                    <td>{span.kind ?? '—'}</td>
                    <td>{span.durationMs} ms</td>
                    <td>{span.status ?? 'ok'}</td>
                    <td>{new Date(span.startAt).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
