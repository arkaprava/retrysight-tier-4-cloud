#!/usr/bin/env node
/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { Server } from '@modelcontextprotocol/sdk/server/index.js'
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js'
import {
  CallToolRequestSchema,
  GetPromptRequestSchema,
  ListPromptsRequestSchema,
  ListResourcesRequestSchema,
  ListToolsRequestSchema,
  ReadResourceRequestSchema,
} from '@modelcontextprotocol/sdk/types.js'
import {
  McpFramework,
  ManagerClient,
  loadConfig,
  toFriendlyError,
} from '@agentretry/mcp-core'
import { registerTools, toolSchemas, handleToolCall } from './tools.js'
import { registerResources, readResource, resourceList, RESOURCE_URIS } from './resources.js'
import { registerPrompts, promptList, getPrompt } from './prompts.js'

async function main() {
  const config = loadConfig()
  const client = new ManagerClient(config)
  const framework = new McpFramework()

  registerTools(framework, client)
  registerResources(framework, client)
  registerPrompts(framework, client)

  const server = new Server(
    {
      name: 'agent-performance-monitor',
      version: '1.0.0',
    },
    {
      capabilities: {
        tools: {},
        resources: {},
        prompts: {},
      },
    },
  )

  server.setRequestHandler(ListToolsRequestSchema, async () => ({
    tools: toolSchemas(framework),
  }))

  server.setRequestHandler(CallToolRequestSchema, async (request) => {
    try {
      return await handleToolCall(framework, request.params.name, request.params.arguments)
    } catch (error) {
      const friendly = toFriendlyError(error)
      return {
        content: [{ type: 'text', text: friendly.message }],
        isError: true,
      }
    }
  })

  server.setRequestHandler(ListResourcesRequestSchema, async () => ({
    resources: [
      ...resourceList(framework),
      {
        uri: `${RESOURCE_URIS.taskPrefix}{task-id}`,
        name: 'task-detail',
        mimeType: 'application/json',
        description: 'Replace {task-id} with a task UUID',
      },
    ],
  }))

  server.setRequestHandler(ReadResourceRequestSchema, async (request) => {
    try {
      return await readResource(framework, client, request.params.uri)
    } catch (error) {
      const friendly = toFriendlyError(error)
      throw new Error(friendly.message)
    }
  })

  server.setRequestHandler(ListPromptsRequestSchema, async () => ({
    prompts: promptList(framework),
  }))

  server.setRequestHandler(GetPromptRequestSchema, async (request) => {
    try {
      const args: Record<string, string> = {}
      const rawArgs = request.params.arguments
      if (Array.isArray(rawArgs)) {
        for (const arg of rawArgs) {
          args[arg.name] = arg.value ?? ''
        }
      } else if (rawArgs && typeof rawArgs === 'object') {
        for (const [key, value] of Object.entries(rawArgs)) {
          args[key] = String(value ?? '')
        }
      }
      return await getPrompt(framework, request.params.name, args)
    } catch (error) {
      const friendly = toFriendlyError(error)
      throw new Error(friendly.message)
    }
  })

  const transport = new StdioServerTransport()
  await server.connect(transport)
}

main().catch((error) => {
  const friendly = toFriendlyError(error)
  console.error(friendly.message)
  process.exit(1)
})
