/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

export type ChatMessage = {
  role: 'user' | 'assistant' | 'system'
  content: string
}

export type ChatPageContext = {
  path?: string
  user?: string
  from?: string
  to?: string
  sourceTool?: string
  taskId?: string
  agentId?: string
}

export type ChatHealth = {
  enabled: boolean
  runtimeReachable: boolean
  model: string
  ollamaBaseUrl: string
  availableModels: string[]
  deterministicFallbackEnabled: boolean
  statusMessage: string
}

export type ChatStreamHandlers = {
  onStatus?: (data: Record<string, unknown>) => void
  onToolStart?: (data: Record<string, unknown>) => void
  onToolResult?: (data: Record<string, unknown>) => void
  onDelta?: (content: string) => void
  onFinal?: (content: string) => void
  onDone?: (data: Record<string, unknown>) => void
  onError?: (message: string) => void
}

export async function fetchChatHealth(token: string): Promise<ChatHealth> {
  const res = await fetch('/api/v1/chat/health', {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error(`Chat health failed (${res.status})`)
  return res.json()
}

export async function fetchChatSuggestions(token: string, path: string): Promise<string[]> {
  const res = await fetch(`/api/v1/chat/suggestions?path=${encodeURIComponent(path)}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) return []
  const data = await res.json()
  return Array.isArray(data.suggestions) ? data.suggestions.map(String) : []
}

function parseSseChunk(buffer: string, handlers: ChatStreamHandlers): string {
  const parts = buffer.split('\n\n')
  const rest = parts.pop() ?? ''
  for (const part of parts) {
    const lines = part.split('\n')
    let event = 'message'
    const dataLines: string[] = []
    for (const line of lines) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
    }
    if (dataLines.length === 0) continue
    let data: Record<string, unknown> = {}
    try {
      data = JSON.parse(dataLines.join('\n'))
    } catch {
      data = { content: dataLines.join('\n') }
    }
    switch (event) {
      case 'status':
        handlers.onStatus?.(data)
        break
      case 'tool_start':
        handlers.onToolStart?.(data)
        break
      case 'tool_result':
        handlers.onToolResult?.(data)
        break
      case 'delta':
        handlers.onDelta?.(String(data.content ?? ''))
        break
      case 'final':
        handlers.onFinal?.(String(data.content ?? ''))
        break
      case 'done':
        handlers.onDone?.(data)
        break
      case 'error':
        handlers.onError?.(String(data.message ?? 'Chat error'))
        break
      default:
        break
    }
  }
  return rest
}

export async function streamChat(
  token: string,
  messages: ChatMessage[],
  pageContext: ChatPageContext | undefined,
  handlers: ChatStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const res = await fetch('/api/v1/chat', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify({ messages, pageContext }),
    signal,
  })
  if (!res.ok) {
    const detail = await res.text().catch(() => '')
    throw new Error(detail?.trim() || `Chat failed (${res.status})`)
  }
  if (!res.body) {
    throw new Error('Chat response had no body')
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    buffer = parseSseChunk(buffer, handlers)
  }
  if (buffer.trim()) {
    parseSseChunk(buffer + '\n\n', handlers)
  }
}

/** Render assistant markdown-ish text with internal deep links as anchors. */
export function linkifyDeepLinks(text: string): string {
  const escaped = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  // Fenced code blocks first so inner markdown is not rewritten.
  const withCode = escaped.replace(/```(\w*)\n([\s\S]*?)```/g, (_m, _lang, body: string) => {
    return `<pre class="insights-chat-code">${body.replace(/\n$/, '')}</pre>`
  })

  return withCode
    .replace(/^### (.+)$/gm, '<strong class="insights-chat-h">$1</strong>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/_([^_]+)_/g, '<em>$1</em>')
    .replace(/\[([^\]]+)\]\((\/[a-zA-Z0-9/_-]*)\)/g, '<a href="$2">$1</a>')
    .replace(/(^|\s)(\/(?:tasks|cost|retries|reports|agents|live|admin)[/\w-]*)/g, '$1<a href="$2">$2</a>')
    .replace(/^[-*] (.+)$/gm, '<div class="insights-chat-li">• $1</div>')
    .replace(/\n/g, '<br/>')
}
