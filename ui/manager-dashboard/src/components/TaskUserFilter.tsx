/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useMemo } from 'react'
import { useQuery } from '@apollo/client'
import { AGENTS_QUERY } from '../graphql/client'
import { agentUserSearchValue, formatAgentUserDetail } from '../agentUser'

type TaskUserFilterProps = {
  value: string
  onChange: (value: string) => void
}

type AgentOption = {
  id: string
  hostname: string
  displayName?: string | null
  osUsername?: string | null
  developerEmail?: string | null
  gitEmail?: string | null
}

export function TaskUserFilter({ value, onChange }: TaskUserFilterProps) {
  const { data } = useQuery(AGENTS_QUERY)

  const userOptions = useMemo(() => {
    const agents: AgentOption[] = data?.agents ?? []
    const seen = new Set<string>()
    const options: { value: string; label: string }[] = []

    for (const agent of agents) {
      const searchValue = agentUserSearchValue(agent)
      if (!searchValue || seen.has(searchValue.toLowerCase())) {
        continue
      }
      seen.add(searchValue.toLowerCase())
      options.push({
        value: searchValue,
        label: `${formatAgentUserDetail(agent)} · ${agent.hostname}`,
      })
    }

    return options.sort((a, b) => a.label.localeCompare(b.label))
  }, [data?.agents])

  return (
    <div className="task-filters">
      <label>
        User
        <input
          type="search"
          placeholder="Search by name or email…"
          value={value}
          onChange={(event) => onChange(event.target.value)}
        />
      </label>
      {userOptions.length > 0 && (
        <label>
          Quick select
          <select
            value={userOptions.some((option) => option.value === value) ? value : ''}
            onChange={(event) => onChange(event.target.value)}
          >
            <option value="">All users</option>
            {userOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
      )}
    </div>
  )
}
