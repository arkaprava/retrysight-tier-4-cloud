/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { AuthClient } from './auth.js'
import type { McpConfig } from './config.js'
import { ManagerError, toFriendlyError } from './errors.js'

export type DashboardSummary = {
  totalTasks: number
  avgRetriesPerTask: number
  totalTokens: TokenUsageSummary
  retryTokenCostUsd: number
  retriesByTool: ToolMetric[]
  tokensByTool: ToolMetric[]
  retryTrend: TimeSeriesPoint[]
  topRetryTasks: Task[]
}

export type TokenUsageSummary = {
  inputTokens: number
  outputTokens: number
  cacheReadTokens: number
  cacheWriteTokens: number
  reasoningTokens: number
  estimatedCostUsd: number
}

export type ToolMetric = { tool: string; value: number }
export type TimeSeriesPoint = { timestamp: string; value: number }

export type Agent = {
  id: string
  name: string
  developerEmail: string
  osUsername?: string | null
  displayName?: string | null
  gitEmail?: string | null
  hostname: string
  lastHeartbeatAt?: string | null
  installedCollectors?: string | null
  createdAt: string
}

export type UserSummary = {
  developerEmail: string
  displayName?: string | null
  osUsername?: string | null
  gitEmail?: string | null
  agentCount: number
}

export type Task = {
  id: string
  description: string
  sourceTool: string
  retryCount: number
  status: string
  externalSessionId: string
  startedAt: string
  completedAt?: string | null
  metadata?: string | null
  projectContext?: ProjectContext | null
  agent: Agent
  tokenUsage?: TokenUsageSummary
  events?: TaskEvent[]
  timeline?: TaskEvent[]
  tokenTurns?: TokenTurn[]
}

export type ProjectContext = {
  projectRoot?: string | null
  repoName?: string | null
  gitBranch?: string | null
  gitCommit?: string | null
  gitCommitShort?: string | null
  remoteUrl?: string | null
  remoteHost?: string | null
  isDirty?: boolean | null
  resolvedFrom?: string | null
  isSubmodule?: boolean | null
}

export type TaskEvent = {
  id: string
  eventType: string
  occurredAt: string
  retry: boolean
  sequenceNum: number
  payload?: string | null
}

export type TokenTurn = {
  id: string
  turnIndex: number
  model?: string | null
  inputTokens: number
  outputTokens: number
  cacheReadTokens: number
  cacheWriteTokens: number
  reasoningTokens: number
  estimatedCostUsd?: number | null
  recordedAt: string
}

export type TaskConnection = {
  items: Task[]
  totalCount: number
}

export type ReportGenerateRequest = {
  dataset: string
  format: 'CSV' | 'JSON' | 'PDF'
  from?: string
  to?: string
  filters?: {
    sourceTool?: string
    agentId?: string
    taskId?: string
  }
  phase?: number
}

export class ManagerClient {
  private readonly auth: AuthClient

  constructor(private readonly config: McpConfig) {
    this.auth = new AuthClient(config)
  }

  async graphql<T>(query: string, variables?: Record<string, unknown>): Promise<T> {
    try {
      const token = await this.auth.getToken()
      const response = await fetch(`${this.config.managerUrl}/graphql`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ query, variables }),
      })

      if (response.status === 401) {
        this.auth.clearCache()
        throw new ManagerError('Session expired. Retry after refreshing APM_MCP_TOKEN.', 'AUTH')
      }
      if (!response.ok) {
        throw new ManagerError(`GraphQL HTTP ${response.status}`, 'HTTP')
      }

      const payload = (await response.json()) as { data?: T; errors?: { message: string }[] }
      if (payload.errors?.length) {
        throw new ManagerError(payload.errors.map((e) => e.message).join('; '), 'GRAPHQL')
      }
      if (!payload.data) {
        throw new ManagerError('GraphQL response missing data', 'GRAPHQL')
      }
      return payload.data
    } catch (error) {
      throw toFriendlyError(error)
    }
  }

  async getDashboardSummary(args: {
    from?: string
    to?: string
    user?: string
  } = {}): Promise<DashboardSummary> {
    const data = await this.graphql<{ dashboardSummary: DashboardSummary }>(
      `query DashboardSummary($from: DateTime, $to: DateTime, $user: String) {
        dashboardSummary(from: $from, to: $to, user: $user) {
          totalTasks avgRetriesPerTask retryTokenCostUsd
          totalTokens { inputTokens outputTokens cacheReadTokens cacheWriteTokens reasoningTokens estimatedCostUsd }
          retriesByTool { tool value }
          tokensByTool { tool value }
          retryTrend { timestamp value }
          topRetryTasks { id description sourceTool retryCount status startedAt agent { hostname developerEmail displayName } }
        }
      }`,
      args,
    )
    return data.dashboardSummary
  }

  async listTasks(args: {
    agentId?: string
    sourceTool?: string
    from?: string
    to?: string
    minRetries?: number
    user?: string
    repo?: string
    branch?: string
    remoteHost?: string
    page?: number
    size?: number
  } = {}): Promise<TaskConnection> {
    const data = await this.graphql<{ tasks: TaskConnection }>(
      `query Tasks($agentId: ID, $sourceTool: SourceTool, $from: DateTime, $to: DateTime, $minRetries: Int, $user: String, $repo: String, $branch: String, $remoteHost: String, $page: Int, $size: Int) {
        tasks(agentId: $agentId, sourceTool: $sourceTool, from: $from, to: $to, minRetries: $minRetries, user: $user, repo: $repo, branch: $branch, remoteHost: $remoteHost, page: $page, size: $size) {
          totalCount
          items {
            id description sourceTool retryCount status startedAt completedAt
            projectContext { repoName gitBranch gitCommitShort remoteHost projectRoot isDirty isSubmodule isWorktree }
            agent { id hostname developerEmail displayName osUsername gitEmail }
            tokenUsage { inputTokens outputTokens estimatedCostUsd }
          }
        }
      }`,
      args,
    )
    return data.tasks
  }

  async getTask(id: string): Promise<Task | null> {
    const data = await this.graphql<{ task: Task | null }>(
      `query TaskDetail($id: ID!) {
        task(id: $id) {
          id description sourceTool retryCount status externalSessionId startedAt completedAt metadata
          projectContext { projectRoot repoName gitBranch gitCommit gitCommitShort remoteUrl remoteHost isDirty resolvedFrom isSubmodule isWorktree }
          agent { id hostname developerEmail displayName osUsername gitEmail installedCollectors lastHeartbeatAt }
          tokenUsage { inputTokens outputTokens cacheReadTokens cacheWriteTokens reasoningTokens estimatedCostUsd }
          timeline { id eventType occurredAt retry sequenceNum payload }
          tokenTurns { id turnIndex model inputTokens outputTokens estimatedCostUsd recordedAt }
        }
      }`,
      { id },
    )
    return data.task
  }

  async listAgents(): Promise<Agent[]> {
    const data = await this.graphql<{ agents: Agent[] }>(
      `query Agents {
        agents {
          id name developerEmail displayName osUsername gitEmail hostname lastHeartbeatAt installedCollectors createdAt
        }
      }`,
    )
    return data.agents
  }

  async listUsers(): Promise<UserSummary[]> {
    const data = await this.graphql<{ users: UserSummary[] }>(
      `query Users {
        users { developerEmail displayName osUsername gitEmail agentCount }
      }`,
    )
    return data.users
  }

  async getCostSummary(args: {
    from?: string
    to?: string
    user?: string
    sourceTool?: string
  } = {}): Promise<unknown> {
    const data = await this.graphql<{ costSummary: unknown }>(
      `query CostSummary($from: DateTime, $to: DateTime, $user: String, $sourceTool: SourceTool) {
        costSummary(from: $from, to: $to, user: $user, sourceTool: $sourceTool) {
          totalCostUsd retryCostUsd retryWasteRatio
          costByTool { tool value }
          costByModel { tool value }
          costByUser { developerEmail displayName costUsd }
        }
      }`,
      args,
    )
    return data.costSummary
  }

  async getRetryAnalysis(args: {
    from?: string
    to?: string
    user?: string
    sourceTool?: string
  } = {}): Promise<unknown> {
    const data = await this.graphql<{ retryAnalysis: unknown }>(
      `query RetryAnalysis($from: DateTime, $to: DateTime, $user: String, $sourceTool: SourceTool) {
        retryAnalysis(from: $from, to: $to, user: $user, sourceTool: $sourceTool) {
          retriesByModel { model value }
          retryCostByModel { model value }
          retryWasteByModel { model value }
          unattributedRetryCount
        }
      }`,
      args,
    )
    return data.retryAnalysis
  }

  async generateReport(request: ReportGenerateRequest): Promise<{ content: string; contentType: string }> {
    try {
      const token = await this.auth.getToken()
      const response = await fetch(`${this.config.managerUrl}/api/v1/reports/generate`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify(request),
      })
      if (!response.ok) {
        throw new ManagerError(`Report generation failed (${response.status})`, 'HTTP')
      }
      const contentType = response.headers.get('content-type') ?? 'application/octet-stream'
      const buffer = await response.arrayBuffer()
      const content = contentType.includes('json')
        ? new TextDecoder().decode(buffer)
        : `[binary ${buffer.byteLength} bytes, content-type: ${contentType}]`
      return { content, contentType }
    } catch (error) {
      throw toFriendlyError(error)
    }
  }
}
