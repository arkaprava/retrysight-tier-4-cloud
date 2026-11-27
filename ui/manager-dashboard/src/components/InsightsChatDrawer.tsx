/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import {
  fetchChatHealth,
  fetchChatSuggestions,
  linkifyDeepLinks,
  streamChat,
} from '../chatApi'
import type { ChatHealth, ChatMessage } from '../chatApi'

type UiMessage = ChatMessage & {
  tools?: string[]
  mode?: string
  streaming?: boolean
}

function taskIdFromPath(pathname: string): string | undefined {
  const m = pathname.match(/^\/tasks\/([0-9a-fA-F-]{36})/)
  return m?.[1]
}

export default function InsightsChatDrawer() {
  const { token } = useAuth()
  const location = useLocation()
  const [open, setOpen] = useState(false)
  const [input, setInput] = useState('')
  const [messages, setMessages] = useState<UiMessage[]>([])
  const [busy, setBusy] = useState(false)
  const [statusLine, setStatusLine] = useState<string | null>(null)
  const [health, setHealth] = useState<ChatHealth | null>(null)
  const [suggestions, setSuggestions] = useState<string[]>([])
  const [error, setError] = useState<string | null>(null)
  const abortRef = useRef<AbortController | null>(null)
  const endRef = useRef<HTMLDivElement | null>(null)

  const pageContext = useMemo(() => {
    const taskId = taskIdFromPath(location.pathname)
    return {
      path: location.pathname,
      taskId,
    }
  }, [location.pathname])

  useEffect(() => {
    if (!token || !open) return
    let cancelled = false
    ;(async () => {
      try {
        const [h, s] = await Promise.all([
          fetchChatHealth(token),
          fetchChatSuggestions(token, location.pathname),
        ])
        if (!cancelled) {
          setHealth(h)
          setSuggestions(s)
        }
      } catch (e) {
        if (!cancelled) setHealth(null)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [token, open, location.pathname])

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, statusLine, open])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'j') {
        e.preventDefault()
        setOpen((v) => !v)
      }
      if (e.key === 'Escape' && open) setOpen(false)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  async function send(text: string) {
    if (!token || !text.trim() || busy) return
    setError(null)
    const userMsg: UiMessage = { role: 'user', content: text.trim() }
    const nextHistory = [...messages, userMsg]
    setMessages([...nextHistory, { role: 'assistant', content: '', streaming: true, tools: [] }])
    setInput('')
    setBusy(true)
    setStatusLine('Thinking…')
    abortRef.current?.abort()
    const ac = new AbortController()
    abortRef.current = ac

    let assistant = ''
    const tools: string[] = []
    let mode: string | undefined

    try {
      await streamChat(
        token,
        nextHistory.map(({ role, content }) => ({ role, content })),
        pageContext,
        {
          onStatus: (d) => {
            mode = String(d.mode ?? mode ?? '')
            setStatusLine(
              d.message
                ? String(d.message)
                : mode === 'deterministic'
                  ? 'Deterministic tool mode'
                  : `Model: ${String(d.model ?? 'local')}`,
            )
          },
          onToolStart: (d) => {
            const name = String(d.name ?? 'tool')
            tools.push(name)
            setStatusLine(`Running ${name.replace(/_/g, ' ')}…`)
            setMessages((prev) => {
              const copy = [...prev]
              const last = copy[copy.length - 1]
              if (last?.role === 'assistant') {
                copy[copy.length - 1] = { ...last, tools: [...tools], mode }
              }
              return copy
            })
          },
          onToolResult: (d) => {
            setStatusLine(`Got ${String(d.name ?? 'tool')} result`)
          },
          onDelta: (chunk) => {
            assistant += chunk
            setMessages((prev) => {
              const copy = [...prev]
              const last = copy[copy.length - 1]
              if (last?.role === 'assistant') {
                copy[copy.length - 1] = {
                  ...last,
                  content: assistant,
                  streaming: true,
                  tools: [...tools],
                  mode,
                }
              }
              return copy
            })
          },
          onFinal: (content) => {
            assistant = content || assistant
            setMessages((prev) => {
              const copy = [...prev]
              const last = copy[copy.length - 1]
              if (last?.role === 'assistant') {
                copy[copy.length - 1] = {
                  ...last,
                  content: assistant,
                  streaming: false,
                  tools: [...tools],
                  mode,
                }
              }
              return copy
            })
          },
          onDone: (d) => {
            mode = String(d.mode ?? mode ?? '')
            setStatusLine(null)
          },
          onError: (msg) => setError(msg),
        },
        ac.signal,
      )
    } catch (e) {
      if ((e as Error).name !== 'AbortError') {
        setError((e as Error).message || 'Chat failed')
        setMessages((prev) => prev.filter((m, i) => !(i === prev.length - 1 && m.role === 'assistant' && !m.content)))
      }
    } finally {
      setBusy(false)
      setStatusLine(null)
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    void send(input)
  }

  if (!token) return null

  return (
    <>
      <button
        type="button"
        className={`insights-chat-fab${open ? ' is-open' : ''}`}
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-controls="insights-chat-drawer"
        title="Insights chat (⌘J)"
      >
        <span aria-hidden="true">◇</span>
        <span className="insights-chat-fab-label">Insights</span>
      </button>

      <div
        id="insights-chat-drawer"
        className={`insights-chat-drawer${open ? ' is-open' : ''}`}
        role="dialog"
        aria-label="Insights chat"
        aria-hidden={!open}
      >
        <header className="insights-chat-header">
          <div>
            <h2>Insights</h2>
            <p className="insights-chat-subtitle">
              Local AI over your dashboard data
              {health ? (
                <>
                  {' · '}
                  <span className={`insights-chat-health insights-chat-health--${health.runtimeReachable ? 'ok' : 'degraded'}`}>
                    {health.runtimeReachable ? health.model : 'tool mode'}
                  </span>
                </>
              ) : null}
            </p>
          </div>
          <button type="button" className="insights-chat-close" onClick={() => setOpen(false)} aria-label="Close">
            ✕
          </button>
        </header>

        {pageContext.taskId ? (
          <div className="insights-chat-context">
            Context: task <Link to={`/tasks/${pageContext.taskId}`}>{pageContext.taskId.slice(0, 8)}…</Link>
          </div>
        ) : (
          <div className="insights-chat-context">Context: {location.pathname || '/'}</div>
        )}

        <div className="insights-chat-messages">
          {messages.length === 0 && (
            <div className="insights-chat-empty">
              <p>Ask about users, tools, retries, cost, or a task on this page.</p>
              <div className="insights-chat-suggestions">
                {suggestions.map((s) => (
                  <button key={s} type="button" onClick={() => void send(s)} disabled={busy}>
                    {s}
                  </button>
                ))}
              </div>
            </div>
          )}
          {messages.map((m, idx) => (
            <div key={idx} className={`insights-chat-msg insights-chat-msg--${m.role}`}>
              {m.tools && m.tools.length > 0 && (
                <div className="insights-chat-tools">
                  {m.tools.map((t, i) => (
                    <span key={`${t}-${i}`}>{t.replace(/_/g, ' ')}</span>
                  ))}
                </div>
              )}
              {m.role === 'assistant' ? (
                <div
                  className="insights-chat-bubble"
                  dangerouslySetInnerHTML={{ __html: linkifyDeepLinks(m.content || (m.streaming ? '…' : '')) }}
                />
              ) : (
                <div className="insights-chat-bubble">{m.content}</div>
              )}
            </div>
          ))}
          {statusLine && <div className="insights-chat-status">{statusLine}</div>}
          {error && <div className="insights-chat-error">{error}</div>}
          <div ref={endRef} />
        </div>

        <form className="insights-chat-composer" onSubmit={onSubmit}>
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Ask about cost, retries, tasks…"
            rows={2}
            disabled={busy}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault()
                void send(input)
              }
            }}
          />
          <div className="insights-chat-actions">
            <button type="button" className="secondary" disabled={busy || messages.length === 0} onClick={() => setMessages([])}>
              Clear
            </button>
            <button type="submit" disabled={busy || !input.trim()}>
              {busy ? 'Sending…' : 'Send'}
            </button>
          </div>
        </form>
      </div>
    </>
  )
}
