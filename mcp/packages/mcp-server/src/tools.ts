/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import {
  McpFramework,
  ManagerClient,
  zodToJsonSchema,
  toFriendlyError,
} from '@agentretry/mcp-core'
import { z } from 'zod'

const sourceToolEnum = z.enum([
  'CURSOR',
  'CLAUDE_CODE',
  'CLAUDE_COWORK',
  'CODEX',
  'KIRO',
  'KIRO_IDE',
  'WARP',
  'COPILOT',
  'WINDSURF',
  'CONTINUE',
  'CLINE',
  'AIDER',
  'OPENCODE',
])

export function registerTools(framework: McpFramework, client: ManagerClient): void {
  framework.registerTool(
    {
      name: 'get_dashboard_summary',
      description: 'Fetch dashboard KPIs: total tasks, avg retries, token usage, retry cost, trends.',
      inputSchema: z.object({
        from: z.string().optional().describe('ISO-8601 start time'),
        to: z.string().optional().describe('ISO-8601 end time'),
        user: z.string().optional().describe('Filter by developer email, display name, or OS username'),
      }),
    },
    async (args) => ({
      content: [{ type: 'text', text: JSON.stringify(await client.getDashboardSummary(args), null, 2) }],
    }),
  )

  framework.registerTool(
    {
      name: 'list_tasks',
      description: 'List coding agent tasks with optional filters and pagination.',
      inputSchema: z.object({
        agentId: z.string().optional(),
        sourceTool: sourceToolEnum.optional(),
        from: z.string().optional(),
        to: z.string().optional(),
        minRetries: z.number().int().optional(),
        user: z.string().optional(),
        page: z.number().int().optional(),
        size: z.number().int().optional(),
      }),
    },
    async (args) => ({
      content: [{ type: 'text', text: JSON.stringify(await client.listTasks(args), null, 2) }],
    }),
  )

  framework.registerTool(
    {
      name: 'get_task',
      description: 'Fetch a single task with timeline, token turns, and agent metadata.',
      inputSchema: z.object({
        id: z.string().describe('Task UUID'),
      }),
    },
    async ({ id }) => ({
      content: [{ type: 'text', text: JSON.stringify(await client.getTask(id), null, 2) }],
    }),
  )

  framework.registerTool(
    {
      name: 'list_agents',
      description: 'List registered collector agents and heartbeat status.',
      inputSchema: z.object({}),
    },
    async () => ({
      content: [{ type: 'text', text: JSON.stringify(await client.listAgents(), null, 2) }],
    }),
  )

  framework.registerTool(
    {
      name: 'list_users',
      description: 'List distinct developers seen across collector agents.',
      inputSchema: z.object({}),
    },
    async () => ({
      content: [{ type: 'text', text: JSON.stringify(await client.listUsers(), null, 2) }],
    }),
  )


  framework.registerTool(
    {
      name: 'get_cost_summary',
      description: 'Fetch cost analytics summary (spend by tool/model/user).',
      inputSchema: z.object({
        from: z.string().optional(),
        to: z.string().optional(),
        user: z.string().optional(),
        sourceTool: sourceToolEnum.optional(),
      }),
    },
    async (args) => ({
      content: [{ type: 'text', text: JSON.stringify(await client.getCostSummary(args), null, 2) }],
    }),
  )

  framework.registerTool(
    {
      name: 'get_retry_analysis',
      description: 'Fetch retry analysis grouped by model.',
      inputSchema: z.object({
        from: z.string().optional(),
        to: z.string().optional(),
        user: z.string().optional(),
        sourceTool: sourceToolEnum.optional(),
      }),
    },
    async (args) => ({
      content: [{ type: 'text', text: JSON.stringify(await client.getRetryAnalysis(args), null, 2) }],
    }),
  )

  framework.registerTool(
    {
      name: 'generate_report',
      description: 'Generate a CSV or JSON report from the manager reporting API.',
      inputSchema: z.object({
        dataset: z.enum(['EXECUTIVE_SUMMARY', 'TASK_LIST', 'TASK_DETAIL', 'COLLECTOR_ACTIVITY', 'RETRY_WASTE', 'RETRY_BY_MODEL', 'TOKEN_COST']),
        format: z.enum(['CSV', 'JSON']).default('JSON'),
        from: z.string().optional(),
        to: z.string().optional(),
        sourceTool: sourceToolEnum.optional(),
        agentId: z.string().optional(),
        taskId: z.string().optional(),
        phase: z.number().int().optional(),
      }),
    },
    async (args) => {
      const { sourceTool, agentId, taskId, ...rest } = args
      const result = await client.generateReport({
        ...rest,
        filters: { sourceTool, agentId, taskId },
      })
      return { content: [{ type: 'text', text: result.content }] }
    },
  )
}

export function toolSchemas(framework: McpFramework) {
  return [...framework.tools.values()].map(({ def }) => ({
    name: def.name,
    description: def.description,
    inputSchema: zodToJsonSchema(def.inputSchema),
  }))
}

export function handleToolCall(
  framework: McpFramework,
  name: string,
  args: unknown,
): Promise<{ content: { type: 'text'; text: string }[] }> {
  const tool = framework.tools.get(name)
  if (!tool) {
    throw toFriendlyError(new Error(`Unknown tool: ${name}`))
  }
  const parsed = tool.def.inputSchema.parse(args ?? {})
  return tool.handler(parsed)
}
