/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useMemo, useState } from 'react'
import { useQuery } from '@apollo/client'
import { SortableTableHeader } from '../components/SortableTableHeader'
import { AGENTS_QUERY } from '../graphql/client'
import { useTableSort } from '../hooks/useTableSort'
import { formatAgentUserDetail } from '../agentUser'

type AgentRow = {
  id: string
  hostname: string
  developerEmail: string
  osUsername?: string | null
  displayName?: string | null
  gitEmail?: string | null
  lastHeartbeatAt: string | null
  installedCollectors: string | null
  stale?: boolean | null
}

const AGENT_SORT_COLUMNS = [
  { key: 'hostname', type: 'text' as const, getValue: (agent: AgentRow) => agent.hostname },
  { key: 'user', type: 'text' as const, getValue: (agent: AgentRow) => formatAgentUserDetail(agent) },
  { key: 'osUsername', type: 'text' as const, getValue: (agent: AgentRow) => agent.osUsername ?? '' },
  { key: 'developerEmail', type: 'text' as const, getValue: (agent: AgentRow) => agent.developerEmail },
  {
    key: 'status',
    type: 'text' as const,
    getValue: (agent: AgentRow) => (agent.stale ? 'stale' : 'online'),
  },
  {
    key: 'lastHeartbeatAt',
    type: 'date' as const,
    getValue: (agent: AgentRow) => agent.lastHeartbeatAt,
  },
  {
    key: 'installedCollectors',
    type: 'text' as const,
    getValue: (agent: AgentRow) => agent.installedCollectors ?? '',
  },
]

function LoadingState({ label }: { label: string }) {
  return (
    <div className="loading-state">
      <div className="loading-spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  )
}

export default function AgentsPage() {
  const { data, loading, error } = useQuery(AGENTS_QUERY, {
    pollInterval: 15000,
  })
  const [staleOnly, setStaleOnly] = useState(false)

  const agents: AgentRow[] = data?.agents ?? []
  const visibleAgents = useMemo(
    () => (staleOnly ? agents.filter((agent) => agent.stale) : agents),
    [agents, staleOnly],
  )
  const staleCount = useMemo(() => agents.filter((agent) => agent.stale).length, [agents])
  const sortColumns = useMemo(() => AGENT_SORT_COLUMNS, [])
  const { sortedData: sortedAgents, sortKey, sortDirection, toggleSort } = useTableSort(
    visibleAgents,
    sortColumns,
  )

  if (loading) return <LoadingState label="Loading collectors…" />
  if (error) return <p className="empty">Failed to load collectors: {error.message}</p>

  return (
    <>
      <header className="page-header">
        <h2 className="page-title">Collectors</h2>
        <p className="page-subtitle">Developer machines running the collector service</p>
      </header>

      <div className="card">
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '0.75rem' }}>
          <label style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.875rem' }}>
            <input
              type="checkbox"
              checked={staleOnly}
              onChange={(event) => setStaleOnly(event.target.checked)}
            />
            Stale only
          </label>
          {staleCount > 0 && (
            <span className="badge abandoned" title="No recent heartbeat">
              {staleCount} stale
            </span>
          )}
        </div>
        {agents.length === 0 ? (
          <p className="empty">No collectors registered yet.</p>
        ) : visibleAgents.length === 0 ? (
          <p className="empty">No stale collectors.</p>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <SortableTableHeader
                    label="Hostname"
                    columnKey="hostname"
                    sortKey={sortKey}
                    sortDirection={sortDirection}
                    onSort={toggleSort}
                  />
                  <SortableTableHeader
                    label="User"
                    columnKey="user"
                    sortKey={sortKey}
                    sortDirection={sortDirection}
                    onSort={toggleSort}
                  />
                  <SortableTableHeader
                    label="OS Username"
                    columnKey="osUsername"
                    sortKey={sortKey}
                    sortDirection={sortDirection}
                    onSort={toggleSort}
                  />
                  <SortableTableHeader
                    label="Email"
                    columnKey="developerEmail"
                    sortKey={sortKey}
                    sortDirection={sortDirection}
                    onSort={toggleSort}
                  />
                  <SortableTableHeader
                    label="Status"
                    columnKey="status"
                    sortKey={sortKey}
                    sortDirection={sortDirection}
                    onSort={toggleSort}
                  />
                  <SortableTableHeader
                    label="Last Heartbeat"
                    columnKey="lastHeartbeatAt"
                    sortKey={sortKey}
                    sortDirection={sortDirection}
                    onSort={toggleSort}
                  />
                  <SortableTableHeader
                    label="Collectors"
                    columnKey="installedCollectors"
                    sortKey={sortKey}
                    sortDirection={sortDirection}
                    onSort={toggleSort}
                  />
                </tr>
              </thead>
              <tbody>
                {sortedAgents.map((agent) => (
                  <tr key={agent.id}>
                    <td><strong>{agent.hostname}</strong></td>
                    <td>{formatAgentUserDetail(agent)}</td>
                    <td>{agent.osUsername ?? '—'}</td>
                    <td>{agent.developerEmail}</td>
                    <td>
                      {agent.stale ? (
                        <span className="badge abandoned">Stale</span>
                      ) : (
                        <span className="badge active">Online</span>
                      )}
                    </td>
                    <td>
                      {agent.lastHeartbeatAt
                        ? new Date(agent.lastHeartbeatAt).toLocaleString()
                        : '—'}
                    </td>
                    <td>{agent.installedCollectors ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  )
}
