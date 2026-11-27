/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import type { McpFramework, ManagerClient } from '@agentretry/mcp-core'

export const RESOURCE_URIS = {
  dashboardSummary: 'agent-performance://dashboard/summary',
  agents: 'agent-performance://agents',
  users: 'agent-performance://users',
  taskPrefix: 'agent-performance://tasks/',
} as const

export function registerResources(framework: McpFramework, client: ManagerClient): void {
  framework.registerResource(RESOURCE_URIS.dashboardSummary, async () => ({
    contents: [{
      uri: RESOURCE_URIS.dashboardSummary,
      mimeType: 'application/json',
      text: JSON.stringify(await client.getDashboardSummary(), null, 2),
    }],
  }))

  framework.registerResource(RESOURCE_URIS.agents, async () => ({
    contents: [{
      uri: RESOURCE_URIS.agents,
      mimeType: 'application/json',
      text: JSON.stringify(await client.listAgents(), null, 2),
    }],
  }))

  framework.registerResource(RESOURCE_URIS.users, async () => ({
    contents: [{
      uri: RESOURCE_URIS.users,
      mimeType: 'application/json',
      text: JSON.stringify(await client.listUsers(), null, 2),
    }],
  }))

  framework.registerResource(RESOURCE_URIS.taskPrefix, async () => ({
    contents: [{
      uri: RESOURCE_URIS.taskPrefix,
      mimeType: 'text/plain',
      text: 'Use agent-performance://tasks/{task-id} to read a specific task.',
    }],
  }))
}

export async function readResource(
  framework: McpFramework,
  client: ManagerClient,
  uri: string,
) {
  if (uri.startsWith(RESOURCE_URIS.taskPrefix)) {
    const id = uri.slice(RESOURCE_URIS.taskPrefix.length)
    const task = await client.getTask(id)
    return {
      contents: [{
        uri,
        mimeType: 'application/json',
        text: JSON.stringify(task, null, 2),
      }],
    }
  }

  const handler = framework.resources.get(uri)
  if (handler) {
    return handler()
  }

  const prefixHandler = framework.resources.get(RESOURCE_URIS.taskPrefix)
  if (prefixHandler) {
    return prefixHandler()
  }

  throw new Error(`Unknown resource: ${uri}`)
}

export function resourceList(framework: McpFramework) {
  return [...framework.resources.keys()].map((uri) => ({
    uri,
    name: uri.replace('agent-performance://', '').replace(/\//g, '-'),
    mimeType: uri.includes('tasks') ? 'application/json' : 'application/json',
  }))
}
