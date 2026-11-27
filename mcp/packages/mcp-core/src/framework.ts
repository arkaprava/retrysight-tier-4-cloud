/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { z } from 'zod'

export type ToolDefinition<T extends z.ZodTypeAny> = {
  name: string
  description: string
  inputSchema: T
}

export type ToolHandler<T extends z.ZodTypeAny> = (
  args: z.infer<T>,
) => Promise<{ content: { type: 'text'; text: string }[] }>

export type ResourceHandler = () => Promise<{
  contents: { uri: string; mimeType: string; text: string }[]
}>

export type PromptDefinition = {
  name: string
  description: string
  arguments?: { name: string; description: string; required?: boolean }[]
}

export type PromptHandler = (
  args: Record<string, string>,
) => Promise<{ messages: { role: 'user' | 'assistant'; content: { type: 'text'; text: string } }[] }>

export class McpFramework {
  readonly tools = new Map<string, { def: ToolDefinition<z.ZodTypeAny>; handler: ToolHandler<z.ZodTypeAny> }>()
  readonly resources = new Map<string, ResourceHandler>()
  readonly prompts = new Map<string, { def: PromptDefinition; handler: PromptHandler }>()

  registerTool<T extends z.ZodTypeAny>(
    def: ToolDefinition<T>,
    handler: ToolHandler<T>,
  ): void {
    this.tools.set(def.name, {
      def: def as ToolDefinition<z.ZodTypeAny>,
      handler: handler as ToolHandler<z.ZodTypeAny>,
    })
  }

  registerResource(uri: string, handler: ResourceHandler): void {
    this.resources.set(uri, handler)
  }

  registerPrompt(def: PromptDefinition, handler: PromptHandler): void {
    this.prompts.set(def.name, { def, handler })
  }
}

export function zodToJsonSchema(schema: z.ZodTypeAny): Record<string, unknown> {
  if ('shape' in schema && schema.shape) {
    const properties: Record<string, unknown> = {}
    const required: string[] = []
    for (const [key, value] of Object.entries(schema.shape as Record<string, z.ZodTypeAny>)) {
      properties[key] = zodTypeToJson(value)
      if (!(value instanceof z.ZodOptional)) {
        required.push(key)
      }
    }
    return { type: 'object', properties, required: required.length ? required : undefined }
  }
  return zodTypeToJson(schema)
}

function zodTypeToJson(schema: z.ZodTypeAny): Record<string, unknown> {
  if (schema instanceof z.ZodString) return { type: 'string', description: schema.description }
  if (schema instanceof z.ZodNumber) return { type: 'number', description: schema.description }
  if (schema instanceof z.ZodBoolean) return { type: 'boolean', description: schema.description }
  if (schema instanceof z.ZodOptional) return zodTypeToJson(schema.unwrap())
  if (schema instanceof z.ZodEnum) return { type: 'string', enum: schema.options }
  return { type: 'string' }
}
