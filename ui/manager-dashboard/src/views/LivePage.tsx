/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useQuery, useSubscription } from '@apollo/client'
import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import TrackerRow, { type TrackerDatum } from '../components/analytics/TrackerRow'
import SessionWaterfall from '../components/SessionWaterfall'
import { AGENT_HEARTBEAT_SUBSCRIPTION, TASK_DETAIL, TASK_UPDATED_SUBSCRIPTION } from '../graphql/client'
import { formatAgentUserDetail } from '../agentUser'
import { formatSourceTool } from '../sourceTools'

type LiveTask = {
  id: string
  description?: string | null
  sourceTool?: string | null
  latestModel?: string | null
  retryCount?: number | null
  status?: string | null
  tokenUsage?: {
    inputTokens?: number | null
    outputTokens?: number | null
  } | null
  agent?: {
    hostname?: string | null
  } | null
}

type LiveAgent = {
  hostname?: string | null
  lastHeartbeatAt?: string | null
  installedCollectors?: string | null
  displayName?: string | null
  osUsername?: string | null
  developerEmail?: string | null
  gitEmail?: string | null
}

function subscriptionErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message
  }
  return 'Live subscription failed'
}

function formatTokenUsage(
  usage?: { inputTokens?: number | null; outputTokens?: number | null } | null,
): string {
  if (!usage) {
    return '—'
  }
  return `${usage.inputTokens ?? 0} in / ${usage.outputTokens ?? 0} out`
}

export default function LivePage() {
  const {
    data: taskData,
    error: taskSubError,
    loading: taskSubLoading,
  } = useSubscription(TASK_UPDATED_SUBSCRIPTION)
  const {
    data: agentData,
    error: agentSubError,
    loading: agentSubLoading,
  } = useSubscription(AGENT_HEARTBEAT_SUBSCRIPTION)

  const latestTask = taskData?.taskUpdated as LiveTask | undefined
  const latestAgent = agentData?.agentHeartbeat as LiveAgent | undefined
  const subscriptionError = taskSubError ?? agentSubError
  const isConnecting = !subscriptionError && taskSubLoading && agentSubLoading
  const subscriptionsReady = !subscriptionError && !isConnecting

  const { data: liveTaskDetail } = useQuery(TASK_DETAIL, {
    variables: { id: latestTask?.id ?? '' },
    skip: !latestTask?.id,
    pollInterval: latestTask?.id ? 5000 : 0,
    notifyOnNetworkStatusChange: false,
  })

  const liveTask = liveTaskDetail?.task

  const activityTracker = useMemo<TrackerDatum[]>(() => {
    const items: TrackerDatum[] = []
    if (latestTask) {
      items.push({
        key: 'task',
        color: 'emerald',
        tooltip: `Task: ${latestTask.description ?? latestTask.id}`,
      })
    }
    if (latestAgent) {
      items.push({
        key: 'agent',
        color: 'cyan',
        tooltip: `Agent: ${latestAgent.hostname ?? 'unknown'}`,
      })
    }
    if (items.length === 0) {
      items.push({
        key: subscriptionError ? 'error' : 'idle',
        color: subscriptionError ? 'rose' : 'amber',
        tooltip: subscriptionError
          ? 'Live stream disconnected'
          : subscriptionsReady
            ? 'Waiting for live events'
            : 'Connecting to live stream…',
      })
    }
    return items
  }, [latestAgent, latestTask, subscriptionError, subscriptionsReady])

  const statusClass = latestTask?.status?.toLowerCase() ?? 'unknown'

  return (
    <div className="analytics-surface">
      <header className="page-header">
        <h2 className="page-title">Live Activity</h2>
        <p className="page-subtitle">Real-time task and agent updates via GraphQL subscriptions</p>
      </header>

      {subscriptionError && (
        <div className="live-connection-banner" role="alert">
          <strong>Live stream unavailable.</strong>{' '}
          {subscriptionErrorMessage(subscriptionError)}. Refresh the page or sign in again if the
          problem persists.
        </div>
      )}

      {!subscriptionError && isConnecting && (
        <p className="live-connection-hint" role="status">
          Connecting to live stream…
        </p>
      )}

      {!subscriptionError && subscriptionsReady && (
        <p className="live-connection-hint live-connection-hint--connected" role="status">
          <span className="live-connection-dot" aria-hidden="true" />
          Connected — waiting for agent activity
        </p>
      )}

      <TrackerRow label="Live signal" data={activityTracker} className="section-gap" />

      <div className="grid grid-2">
        <div className="card">
          <h3>Latest Task Update</h3>
          {!latestTask ? (
            <div className="live-waiting">
              <div className="live-waiting-icon" aria-hidden="true">◎</div>
              <p className="empty">Waiting for task events from agents…</p>
            </div>
          ) : (
            <div className="live-feed">
              <div className="live-feed-item">
                <strong>{latestTask.description ?? 'Untitled task'}</strong>
                <div className="live-feed-meta">
                  <span>
                    <span className="live-feed-label">Tool</span>
                    {latestTask.sourceTool ? formatSourceTool(latestTask.sourceTool) : '—'}
                  </span>
                  {latestTask.latestModel && (
                    <span><span className="live-feed-label">Model</span>{latestTask.latestModel}</span>
                  )}
                  <span>
                    <span className="live-feed-label">Retries</span>
                    {latestTask.retryCount ?? 0}
                  </span>
                  <span>
                    <span className="live-feed-label">Status</span>
                    <span className={`badge ${statusClass}`}>{latestTask.status ?? 'UNKNOWN'}</span>
                  </span>
                  <span>
                    <span className="live-feed-label">Tokens</span>
                    {formatTokenUsage(latestTask.tokenUsage)}
                  </span>
                  <span>
                    <span className="live-feed-label">Agent</span>
                    {latestTask.agent?.hostname ?? '—'}
                  </span>
                </div>
                <Link to={`/tasks/${latestTask.id}`} className="live-feed-link">
                  View detail →
                </Link>
              </div>
            </div>
          )}
        </div>

        <div className="card">
          <h3>Latest Agent Heartbeat</h3>
          {!latestAgent ? (
            <div className="live-waiting">
              <div className="live-waiting-icon" aria-hidden="true">⬡</div>
              <p className="empty">Waiting for agent heartbeats…</p>
            </div>
          ) : (
            <div className="live-feed">
              <div className="live-feed-item">
                <strong>{latestAgent.hostname ?? 'Unknown collector'}</strong>
                <div className="live-feed-meta">
                  <span><span className="live-feed-label">User</span>{formatAgentUserDetail(latestAgent)}</span>
                  <span><span className="live-feed-label">Last seen</span>{latestAgent.lastHeartbeatAt ?? '—'}</span>
                  <span><span className="live-feed-label">Collectors</span>{latestAgent.installedCollectors ?? '—'}</span>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {latestTask?.id && (
        <SessionWaterfall
          spans={liveTask?.spanTree}
          events={liveTask?.timeline}
        />
      )}
    </div>
  )
}
