/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

export type AgentUserInfo = {
  displayName?: string | null
  osUsername?: string | null
  developerEmail?: string | null
  gitEmail?: string | null
}

export function formatAgentUser(agent: AgentUserInfo | null | undefined): string {
  if (!agent) return '—'
  return agent.displayName?.trim()
    || agent.osUsername?.trim()
    || agent.developerEmail?.trim()
    || agent.gitEmail?.trim()
    || '—'
}

export function formatAgentUserDetail(agent: AgentUserInfo | null | undefined): string {
  if (!agent) return '—'
  const primary = formatAgentUser(agent)
  const email = agent.developerEmail?.trim() || agent.gitEmail?.trim()
  if (email && email !== primary) {
    return `${primary} (${email})`
  }
  return primary
}

export function agentUserSearchValue(agent: AgentUserInfo): string {
  return agent.displayName?.trim()
    || agent.osUsername?.trim()
    || agent.developerEmail?.trim()
    || agent.gitEmail?.trim()
    || ''
}
