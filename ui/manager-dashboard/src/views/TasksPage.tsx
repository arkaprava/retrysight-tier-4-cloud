/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useMemo, useState } from 'react'
import { useQuery } from '@apollo/client'
import { Link } from 'react-router-dom'
import { SortableTableHeader } from '../components/SortableTableHeader'
import ModelBadgeList from '../components/analytics/ModelBadgeList'
import { TaskTablePagination } from '../components/TaskTablePagination'
import { EMPTY_TASK_FILTERS, TaskFiltersBar, type TaskFilters } from '../components/TaskFiltersBar'
import { TASKS_QUERY } from '../graphql/client'
import { useTableSort } from '../hooks/useTableSort'
import { formatAgentUserDetail } from '../agentUser'
import { formatTaskStartedAt } from '../utils/dates'
import { formatSourceTool } from '../sourceTools'
import { formatUsd } from '../utils/contextUsage'
import { usePlatformFeatures } from '../platformFeatures'
import { toInstantEnd, toInstantStart } from '../reportApi'

const PAGE_SIZE = 15

type ProjectContextData = {
  repoName?: string | null
  gitBranch?: string | null
}

type TaskRow = {
  id: string
  description: string
  sourceTool: string
  modelsUsed?: string[]
  latestModel?: string | null
  retryCount: number
  status: string
  startedAt?: string | null
  projectContext?: ProjectContextData | null
  tokenUsage: { inputTokens: number; outputTokens: number; estimatedCostUsd?: number }
  agent: {
    hostname: string
    displayName?: string | null
    osUsername?: string | null
    developerEmail?: string | null
    gitEmail?: string | null
  }
}

function formatRepoBranch(projectContext?: ProjectContextData | null): string {
  if (!projectContext) return '—'
  const parts = [projectContext.repoName, projectContext.gitBranch].filter(Boolean)
  return parts.length > 0 ? parts.join(' · ') : '—'
}

function LoadingState({ label }: { label: string }) {
  return (
    <div className="loading-state">
      <div className="loading-spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  )
}

function parseMinRetries(value: string): number | undefined {
  const trimmed = value.trim()
  if (!trimmed) return undefined
  const parsed = Number.parseInt(trimmed, 10)
  return Number.isNaN(parsed) ? undefined : parsed
}

export default function TasksPage() {
  const [page, setPage] = useState(1)
  const [filters, setFilters] = useState<TaskFilters>(EMPTY_TASK_FILTERS)
  const { gitContextCaptureEnabled, gitContextQueryEnabled } = usePlatformFeatures()

  const trimmedUser = filters.user.trim()
  const trimmedRepo = filters.repo.trim()
  const trimmedBranch = filters.branch.trim()
  const trimmedRemoteHost = filters.remoteHost.trim()

  const { data, loading, error } = useQuery(TASKS_QUERY, {
    variables: {
      user: trimmedUser || undefined,
      from: filters.fromDate ? toInstantStart(filters.fromDate) : undefined,
      to: filters.toDate ? toInstantEnd(filters.toDate) : undefined,
      sourceTool: filters.sourceTool || undefined,
      agentId: filters.agentId || undefined,
      minRetries: parseMinRetries(filters.minRetries),
      repo: gitContextQueryEnabled && trimmedRepo ? trimmedRepo : undefined,
      branch: gitContextQueryEnabled && trimmedBranch ? trimmedBranch : undefined,
      remoteHost: gitContextQueryEnabled && trimmedRemoteHost ? trimmedRemoteHost : undefined,
      page,
      size: PAGE_SIZE,
    },
    pollInterval: 15000,
  })

  const tasks: TaskRow[] = data?.tasks?.items ?? []
  const totalCount: number = data?.tasks?.totalCount ?? 0

  const sortColumns = useMemo(
    () => [
      { key: 'description', type: 'text' as const, getValue: (task: TaskRow) => task.description },
      { key: 'sourceTool', type: 'text' as const, getValue: (task: TaskRow) => task.sourceTool },
      { key: 'models', type: 'text' as const, getValue: (task: TaskRow) => (task.modelsUsed ?? []).join(', ') || task.latestModel || '' },
      ...(gitContextCaptureEnabled
        ? [{
            key: 'repoBranch',
            type: 'text' as const,
            getValue: (task: TaskRow) => formatRepoBranch(task.projectContext),
          }]
        : []),
      { key: 'user', type: 'text' as const, getValue: (task: TaskRow) => formatAgentUserDetail(task.agent) },
      { key: 'agent', type: 'text' as const, getValue: (task: TaskRow) => task.agent?.hostname ?? '' },
      { key: 'startedAt', type: 'date' as const, getValue: (task: TaskRow) => task.startedAt },
      { key: 'retryCount', type: 'number' as const, getValue: (task: TaskRow) => task.retryCount },
      {
        key: 'tokens',
        type: 'number' as const,
        getValue: (task: TaskRow) =>
          Number(task.tokenUsage?.inputTokens ?? 0) + Number(task.tokenUsage?.outputTokens ?? 0),
      },
      {
        key: 'cost',
        type: 'number' as const,
        getValue: (task: TaskRow) => Number(task.tokenUsage?.estimatedCostUsd ?? 0),
      },
      { key: 'status', type: 'status' as const, getValue: (task: TaskRow) => task.status },
    ],
    [gitContextCaptureEnabled],
  )

  const { sortedData: sortedTasks, sortKey, sortDirection, toggleSort } = useTableSort(
    tasks,
    sortColumns,
  )

  function updateFilters(patch: Partial<TaskFilters>) {
    setFilters((current) => ({ ...current, ...patch }))
    setPage(1)
  }

  function resetFilters() {
    setFilters(EMPTY_TASK_FILTERS)
    setPage(1)
  }

  const hasActiveFilters = Object.values(filters).some((value) => value.trim() !== '')

  if (loading && tasks.length === 0) return <LoadingState label="Loading tasks…" />
  if (error) return <p className="empty">Failed to load tasks: {error.message}</p>

  return (
    <>
      <header className="page-header">
        <h2 className="page-title">Tasks</h2>
        <p className="page-subtitle">Coding agent sessions with retry and token metrics</p>
      </header>

      <div className="card section-gap">
        <TaskFiltersBar
          filters={filters}
          onChange={updateFilters}
          onReset={resetFilters}
        />
      </div>

      <div className="card">
        {totalCount === 0 ? (
          <p className="empty">
            {hasActiveFilters
              ? 'No tasks match the current filters.'
              : 'No tasks yet.'}
          </p>
        ) : (
          <>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <SortableTableHeader
                      label="Description"
                      columnKey="description"
                      sortKey={sortKey}
                      sortDirection={sortDirection}
                      onSort={toggleSort}
                    />
                    <SortableTableHeader
                      label="Tool"
                      columnKey="sourceTool"
                      sortKey={sortKey}
                      sortDirection={sortDirection}
                      onSort={toggleSort}
                    />
                    <SortableTableHeader
                      label="LLM Models"
                      columnKey="models"
                      sortKey={sortKey}
                      sortDirection={sortDirection}
                      onSort={toggleSort}
                    />
                    {gitContextCaptureEnabled && (
                      <SortableTableHeader
                        label="Repo / Branch"
                        columnKey="repoBranch"
                        sortKey={sortKey}
                        sortDirection={sortDirection}
                        onSort={toggleSort}
                      />
                    )}
                    <SortableTableHeader
                      label="User"
                      columnKey="user"
                      sortKey={sortKey}
                      sortDirection={sortDirection}
                      onSort={toggleSort}
                    />
                    <SortableTableHeader
                      label="Agent"
                      columnKey="agent"
                      sortKey={sortKey}
                      sortDirection={sortDirection}
                      onSort={toggleSort}
                    />
                    <SortableTableHeader
                      label="Started"
                      columnKey="startedAt"
                      sortKey={sortKey}
                      sortDirection={sortDirection}
                      onSort={toggleSort}
                    />
                    <SortableTableHeader
                      label="Retries"
                      columnKey="retryCount"
                      sortKey={sortKey}
                      sortDirection={sortDirection}
                      onSort={toggleSort}
                    />
                    <SortableTableHeader
                      label="Tokens In/Out"
                      columnKey="tokens"
                      sortKey={sortKey}
                      sortDirection={sortDirection}
                      onSort={toggleSort}
                    />
                    <SortableTableHeader
                      label="Est. Cost"
                      columnKey="cost"
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
                  </tr>
                </thead>
                <tbody>
                  {sortedTasks.map((task) => (
                    <tr key={task.id}>
                      <td>
                        <Link to={`/tasks/${task.id}`}>{task.description}</Link>
                      </td>
                      <td>{formatSourceTool(task.sourceTool)}</td>
                      <td>
                        <ModelBadgeList models={task.modelsUsed ?? []} latestModel={task.latestModel} />
                      </td>
                      {gitContextCaptureEnabled && <td>{formatRepoBranch(task.projectContext)}</td>}
                      <td>{formatAgentUserDetail(task.agent)}</td>
                      <td>{task.agent?.hostname ?? '—'}</td>
                      <td>{formatTaskStartedAt(task.startedAt)}</td>
                      <td>{task.retryCount}</td>
                      <td>
                        {Number(task.tokenUsage?.inputTokens ?? 0).toLocaleString()} /{' '}
                        {Number(task.tokenUsage?.outputTokens ?? 0).toLocaleString()}
                      </td>
                      <td>{formatUsd(Number(task.tokenUsage?.estimatedCostUsd ?? 0))}</td>
                      <td>
                        <span className={`badge ${task.status.toLowerCase()}`}>{task.status}</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <TaskTablePagination
              page={page}
              pageSize={PAGE_SIZE}
              totalCount={totalCount}
              loading={loading}
              onPageChange={setPage}
            />
          </>
        )}
      </div>
    </>
  )
}
