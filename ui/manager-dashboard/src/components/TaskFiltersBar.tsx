/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useQuery } from '@apollo/client'
import { AGENTS_QUERY } from '../graphql/client'
import { usePlatformFeatures, useSourceTools } from '../platformFeatures'
import { TaskUserFilter } from './TaskUserFilter'

export type TaskFilters = {
  user: string
  fromDate: string
  toDate: string
  sourceTool: string
  agentId: string
  minRetries: string
  repo: string
  branch: string
  remoteHost: string
}

export const EMPTY_TASK_FILTERS: TaskFilters = {
  user: '',
  fromDate: '',
  toDate: '',
  sourceTool: '',
  agentId: '',
  minRetries: '',
  repo: '',
  branch: '',
  remoteHost: '',
}

type TaskFiltersBarProps = {
  filters: TaskFilters
  onChange: (patch: Partial<TaskFilters>) => void
  onReset: () => void
}

export function TaskFiltersBar({ filters, onChange, onReset }: TaskFiltersBarProps) {
  const { gitContextQueryEnabled } = usePlatformFeatures()
  const { enabledSourceTools } = useSourceTools()
  const { data: agentsData } = useQuery(AGENTS_QUERY)
  const agents = agentsData?.agents ?? []

  const hasActiveFilters = Object.values(filters).some((value) => value.trim() !== '')

  return (
    <div className="task-filters-bar">
      <div className="task-filters-bar-header">
        <h3>Filters</h3>
        {hasActiveFilters && (
          <button type="button" className="filter-clear-btn" onClick={onReset}>
            Clear all
          </button>
        )}
      </div>

      <TaskUserFilter
        value={filters.user}
        onChange={(value) => onChange({ user: value })}
      />

      <div className="task-filters-grid">
        <label>
          From
          <input
            type="date"
            value={filters.fromDate}
            onChange={(event) => onChange({ fromDate: event.target.value })}
          />
        </label>
        <label>
          To
          <input
            type="date"
            value={filters.toDate}
            onChange={(event) => onChange({ toDate: event.target.value })}
          />
        </label>
        <label>
          Tool
          <select
            value={filters.sourceTool}
            onChange={(event) => onChange({ sourceTool: event.target.value })}
          >
            <option value="">All tools</option>
            {enabledSourceTools.map((tool) => (
              <option key={tool.id} value={tool.id}>
                {tool.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          Collector
          <select
            value={filters.agentId}
            onChange={(event) => onChange({ agentId: event.target.value })}
          >
            <option value="">All collectors</option>
            {agents.map((agent: { id: string; hostname: string }) => (
              <option key={agent.id} value={agent.id}>
                {agent.hostname}
              </option>
            ))}
          </select>
        </label>
        <label>
          Min retries
          <input
            type="number"
            min={0}
            step={1}
            placeholder="0"
            value={filters.minRetries}
            onChange={(event) => onChange({ minRetries: event.target.value })}
          />
        </label>
        {gitContextQueryEnabled && (
          <>
            <label>
              Repository
              <input
                type="text"
                placeholder="org/repo"
                value={filters.repo}
                onChange={(event) => onChange({ repo: event.target.value })}
              />
            </label>
            <label>
              Branch
              <input
                type="text"
                placeholder="main"
                value={filters.branch}
                onChange={(event) => onChange({ branch: event.target.value })}
              />
            </label>
            <label>
              Remote host
              <input
                type="text"
                placeholder="github.com"
                value={filters.remoteHost}
                onChange={(event) => onChange({ remoteHost: event.target.value })}
              />
            </label>
          </>
        )}
      </div>
    </div>
  )
}
