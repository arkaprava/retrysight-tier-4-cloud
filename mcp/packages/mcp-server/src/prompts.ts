/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import type { McpFramework, ManagerClient } from '@agentretry/mcp-core'

export function registerPrompts(framework: McpFramework, client: ManagerClient): void {
  framework.registerPrompt(
    {
      name: 'analyze-retry-session',
      description: 'Summarize why a task accumulated retries and token waste.',
      arguments: [
        { name: 'taskId', description: 'Task UUID to analyze', required: true },
      ],
    },
    async (args) => {
      const task = await client.getTask(args.taskId)
      return {
        messages: [{
          role: 'user',
          content: {
            type: 'text',
            text: `Analyze this coding agent session for retry patterns, likely causes, and cost impact:\n\n${JSON.stringify(task, null, 2)}`,
          },
        }],
      }
    },
  )

  framework.registerPrompt(
    {
      name: 'compare-tools',
      description: 'Compare retry and token metrics across AI tools for a time window.',
      arguments: [
        { name: 'user', description: 'Optional developer filter', required: false },
      ],
    },
    async (args) => {
      const summary = await client.getDashboardSummary({ user: args.user })
      return {
        messages: [{
          role: 'user',
          content: {
            type: 'text',
            text: `Compare AI tool performance using retries-by-tool and tokens-by-tool. Highlight outliers and suggest process improvements:\n\n${JSON.stringify({
              retriesByTool: summary.retriesByTool,
              tokensByTool: summary.tokensByTool,
              retryTokenCostUsd: summary.retryTokenCostUsd,
              totalTasks: summary.totalTasks,
            }, null, 2)}`,
          },
        }],
      }
    },
  )
}

export function promptList(framework: McpFramework) {
  return [...framework.prompts.values()].map(({ def }) => ({
    name: def.name,
    description: def.description,
    arguments: def.arguments,
  }))
}

export async function getPrompt(
  framework: McpFramework,
  name: string,
  args: Record<string, string>,
) {
  const prompt = framework.prompts.get(name)
  if (!prompt) {
    throw new Error(`Unknown prompt: ${name}`)
  }
  return prompt.handler(args)
}
