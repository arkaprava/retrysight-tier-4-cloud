/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { ApolloClient, InMemoryCache, HttpLink, split } from '@apollo/client'
import { setContext } from '@apollo/client/link/context'
import { GraphQLWsLink } from '@apollo/client/link/subscriptions'
import { getMainDefinition } from '@apollo/client/utilities'
import { createClient } from 'graphql-ws'
import { gql } from '@apollo/client'

const token = () => localStorage.getItem('authToken') ?? ''

const httpLink = new HttpLink({
  uri: '/graphql',
})

const authLink = setContext((_, { headers }) => ({
  headers: {
    ...headers,
    authorization: token() ? `Bearer ${token()}` : '',
  },
}))

const wsLink = new GraphQLWsLink(
  createClient({
    url: `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/graphql`,
    lazy: true,
    connectionParams: () => {
      const authToken = token()
      return authToken ? { Authorization: `Bearer ${authToken}` } : {}
    },
  }),
)

const splitLink = split(
  ({ query }) => {
    const definition = getMainDefinition(query)
    return definition.kind === 'OperationDefinition' && definition.operation === 'subscription'
  },
  wsLink,
  httpLink,
)

export const apolloClient = new ApolloClient({
  link: authLink.concat(splitLink),
  cache: new InMemoryCache(),
})

export const LOGIN_MUTATION = gql`
  mutation Login($email: String!, $password: String!) {
    login(email: $email, password: $password) {
      token
      email
      displayName
    }
  }
`

export const TASK_UPDATED_SUBSCRIPTION = gql`
  subscription TaskUpdated {
    taskUpdated {
      id
      description
      sourceTool
      modelsUsed
      latestModel
      retryCount
      status
      tokenUsage {
        inputTokens
        outputTokens
        estimatedCostUsd
      }
      tokenTurns {
        model
        turnIndex
      }
      agent {
        hostname
      }
    }
  }
`

export const AGENT_HEARTBEAT_SUBSCRIPTION = gql`
  subscription AgentHeartbeat {
    agentHeartbeat {
      id
      hostname
      developerEmail
      osUsername
      displayName
      gitEmail
      lastHeartbeatAt
      installedCollectors
    }
  }
`

export const DASHBOARD_SUMMARY = gql`
  query DashboardSummary($from: DateTime, $to: DateTime, $user: String) {
    dashboardSummary(from: $from, to: $to, user: $user) {
      totalTasks
      avgRetriesPerTask
      totalTokens {
        inputTokens
        outputTokens
        cacheReadTokens
        cacheWriteTokens
        reasoningTokens
        estimatedCostUsd
      }
      retryTokenCostUsd
      retriesByTool {
        tool
        value
      }
      tokensByTool {
        tool
        value
      }
      llmRunsByToolAndModel {
        sourceTool
        model
        turnCount
        costUsd
      }
      retryTrend {
        timestamp
        value
      }
      retryTrendByTool {
        name
        points {
          timestamp
          value
        }
      }
      retryTrendByModel {
        name
        points {
          timestamp
          value
        }
      }
      evalSummary {
        totalRuns
        passCount
        failCount
        warnCount
        passRate
      }
      evalPassRateTrend {
        timestamp
        value
      }
    }
  }
`

export const COST_SUMMARY = gql`
  query CostSummary($from: DateTime, $to: DateTime, $user: String, $sourceTool: SourceTool) {
    costSummary(from: $from, to: $to, user: $user, sourceTool: $sourceTool) {
      totalCostUsd
      retryCostUsd
      retryWasteRatio
      costByTool {
        tool
        value
      }
      costByModel {
        tool
        value
      }
      llmRunsByToolAndModel {
        sourceTool
        model
        turnCount
        costUsd
      }
      costByUser {
        developerEmail
        displayName
        costUsd
      }
      costTrend {
        timestamp
        value
      }
      costTrendByTool {
        name
        points {
          timestamp
          value
        }
      }
      costTrendByModel {
        name
        points {
          timestamp
          value
        }
      }
      retryCostTrend {
        timestamp
        value
      }
      retryCostTrendByTool {
        name
        points {
          timestamp
          value
        }
      }
      retryCostTrendByModel {
        name
        points {
          timestamp
          value
        }
      }
      topCostlyTasks {
        taskId
        description
        sourceTool
        totalCostUsd
        retryCostUsd
        retryCount
      }
    }
  }
`

export const RETRY_ANALYSIS = gql`
  query RetryAnalysis($from: DateTime, $to: DateTime, $user: String, $sourceTool: SourceTool) {
    retryAnalysis(from: $from, to: $to, user: $user, sourceTool: $sourceTool) {
      retriesByModel {
        model
        value
      }
      retryCostByModel {
        model
        value
      }
      retryWasteByModel {
        model
        value
      }
      retryRateByModel {
        model
        retryRate
        totalTurns
      }
      retryTypeByModel {
        model
        eventType
        count
      }
      topRetryTasksByModel {
        model
        taskId
        description
        sourceTool
        retryCount
        retryCostUsd
      }
      unattributedRetryCount
    }
  }
`

export const COST_BUDGETS = gql`
  query CostBudgets {
    costBudgets {
      id
      name
      scope
      scopeValue
      period
      limitUsd
      alertThresholdPct
      active
      currentSpendUsd
      createdAt
      updatedAt
    }
  }
`

export const COST_ALERTS = gql`
  query CostAlerts {
    costAlerts {
      id
      budgetId
      budgetName
      triggeredAt
      spendUsd
      limitUsd
      message
    }
  }
`

export const COST_FEATURES = gql`
  query CostFeatures {
    costFeatures {
      enterpriseEnabled
      billingApiEnabled
      billingReconciliationAvailable
    }
  }
`

export const BILLING_RECONCILIATION = gql`
  query BillingReconciliation($from: DateTime, $to: DateTime) {
    billingReconciliation(from: $from, to: $to) {
      estimatedCostUsd
      actualCostUsd
      varianceUsd
      variancePct
      actuals {
        id
        periodStart
        periodEnd
        actualCostUsd
        source
        notes
        importedAt
        importedBy
      }
    }
  }
`

export const CREATE_COST_BUDGET = gql`
  mutation CreateCostBudget(
    $name: String!
    $scope: BudgetScope!
    $scopeValue: String
    $period: BudgetPeriod!
    $limitUsd: Float!
    $alertThresholdPct: Float
    $active: Boolean
  ) {
    createCostBudget(
      name: $name
      scope: $scope
      scopeValue: $scopeValue
      period: $period
      limitUsd: $limitUsd
      alertThresholdPct: $alertThresholdPct
      active: $active
    ) {
      id
      name
    }
  }
`

export const UPDATE_COST_BUDGET = gql`
  mutation UpdateCostBudget(
    $id: ID!
    $name: String
    $scope: BudgetScope
    $scopeValue: String
    $period: BudgetPeriod
    $limitUsd: Float
    $alertThresholdPct: Float
    $active: Boolean
  ) {
    updateCostBudget(
      id: $id
      name: $name
      scope: $scope
      scopeValue: $scopeValue
      period: $period
      limitUsd: $limitUsd
      alertThresholdPct: $alertThresholdPct
      active: $active
    ) {
      id
      name
    }
  }
`

export const DELETE_COST_BUDGET = gql`
  mutation DeleteCostBudget($id: ID!) {
    deleteCostBudget(id: $id)
  }
`

export const IMPORT_BILLING_ACTUAL = gql`
  mutation ImportBillingActual(
    $periodStart: DateTime!
    $periodEnd: DateTime!
    $actualCostUsd: Float!
    $notes: String
  ) {
    importBillingActual(
      periodStart: $periodStart
      periodEnd: $periodEnd
      actualCostUsd: $actualCostUsd
      notes: $notes
    ) {
      id
      actualCostUsd
    }
  }
`

export const TASKS_QUERY = gql`
  query Tasks(
    $agentId: ID
    $sourceTool: SourceTool
    $from: DateTime
    $to: DateTime
    $minRetries: Int
    $user: String
    $repo: String
    $branch: String
    $remoteHost: String
    $page: Int
    $size: Int
  ) {
    tasks(
      agentId: $agentId
      sourceTool: $sourceTool
      from: $from
      to: $to
      minRetries: $minRetries
      user: $user
      repo: $repo
      branch: $branch
      remoteHost: $remoteHost
      page: $page
      size: $size
    ) {
      totalCount
      items {
        id
        description
        sourceTool
        modelsUsed
        latestModel
        retryCount
        status
        startedAt
        completedAt
        projectContext {
          repoName
          gitBranch
          gitCommitShort
          remoteHost
        }
        tokenUsage {
          inputTokens
          outputTokens
          estimatedCostUsd
        }
        agent {
          id
          hostname
          developerEmail
          osUsername
          displayName
          gitEmail
        }
      }
    }
  }
`

export const AGENTS_QUERY = gql`
  query Agents {
    agents {
      id
      name
      hostname
      developerEmail
      osUsername
      displayName
      gitEmail
      lastHeartbeatAt
      installedCollectors
      createdAt
      stale
    }
  }
`

export const TASK_DETAIL = gql`
  query TaskDetail($id: ID!) {
    task(id: $id) {
      id
      description
      sourceTool
      modelsUsed
      latestModel
      retryCount
      status
      startedAt
      completedAt
      metadata
      projectContext {
        projectRoot
        repoName
        gitBranch
        gitCommit
        gitCommitShort
        remoteUrl
        remoteHost
        isDirty
        resolvedFrom
        isSubmodule
        isWorktree
      }
      tokenUsage {
        inputTokens
        outputTokens
        cacheReadTokens
        cacheWriteTokens
        reasoningTokens
        estimatedCostUsd
      }
      spanTree { id name kind startMs durationMs status parentId }
      evalRuns {
        id
        label
        score
        explanation
        status
        triggeredBy
        createdAt
        definition { name slug kind }
      }
      timeline {
        id
        eventType
        occurredAt
        retry
        sequenceNum
        payload
        tokenUsage {
          inputTokens
          outputTokens
          cacheReadTokens
          cacheWriteTokens
          reasoningTokens
          totalTokens
          estimatedCostUsd
          model
        }
      }
      tokenTurns {
        turnIndex
        model
        inputTokens
        outputTokens
        cacheReadTokens
        cacheWriteTokens
        reasoningTokens
        estimatedCostUsd
        recordedAt
      }
      agent {
        hostname
        developerEmail
        osUsername
        displayName
        gitEmail
      }
    }
  }
`


export const API_KEYS = gql`
  query ApiKeys {
    apiKeys {
      id
      name
      keyPrefix
      role
      active
      createdAt
      lastUsedAt
      revokedAt
    }
  }
`

export const CREATE_API_KEY = gql`
  mutation CreateApiKey($name: String!, $role: String) {
    createApiKey(name: $name, role: $role) {
      rawKey
      key { id name keyPrefix role active }
    }
  }
`

export const ROTATE_API_KEY = gql`
  mutation RotateApiKey($id: ID!) {
    rotateApiKey(id: $id) {
      rawKey
      key { id name keyPrefix role active }
    }
  }
`

export const REVOKE_API_KEY = gql`
  mutation RevokeApiKey($id: ID!) {
    revokeApiKey(id: $id)
  }
`

export const FETCH_CURSOR_BILLING = gql`
  mutation FetchCursorBillingActual($periodStart: DateTime!, $periodEnd: DateTime!) {
    fetchCursorBillingActual(periodStart: $periodStart, periodEnd: $periodEnd) {
      id
      actualCostUsd
      source
    }
  }
`

export const ARCHIVE_POLICY = gql`
  query ArchivePolicy {
    archivePolicy {
      enabled
      mode
      scheduleCron
      taskEventsDays
      tokenUsageDays
      taskHeadersDays
      datasets
      destinationType
      localPath
      networkPath
      s3Bucket
      s3Prefix
      s3Region
      s3Endpoint
      s3AccessKeyEnv
      s3SecretKeyEnv
      legalHold
      updatedAt
      capabilities {
        retentionPurge
        archiveLocal
        archiveNetwork
        archiveObjectStorage
        archiveRestore
        rollupAnalytics
        partitionMaintenance
      }
    }
  }
`

export const UPDATE_ARCHIVE_POLICY = gql`
  mutation UpdateArchivePolicy($input: ArchivePolicyInput!) {
    updateArchivePolicy(input: $input) {
      enabled
      mode
      scheduleCron
      taskEventsDays
      tokenUsageDays
      taskHeadersDays
      datasets
      destinationType
      localPath
      networkPath
      s3Bucket
      s3Prefix
      s3Region
      s3Endpoint
      s3AccessKeyEnv
      s3SecretKeyEnv
      legalHold
      updatedAt
      capabilities {
        retentionPurge
        archiveLocal
        archiveNetwork
        archiveObjectStorage
        archiveRestore
        rollupAnalytics
        partitionMaintenance
      }
    }
  }
`

export const TEST_ARCHIVE_DESTINATION = gql`
  mutation TestArchiveDestination {
    testArchiveDestination
  }
`

export const RUN_ARCHIVE_JOB = gql`
  mutation RunArchiveJob($dryRun: Boolean) {
    runArchiveJob(dryRun: $dryRun) {
      id
      startedAt
      finishedAt
      status
      dryRun
      mode
      destinationType
      rowsExported
      rowsDeleted
      bytesWritten
      datasets
      errorMessage
      manifestSummary
    }
  }
`

export const ARCHIVE_RUNS = gql`
  query ArchiveRuns {
    archiveRuns {
      id
      startedAt
      finishedAt
      status
      dryRun
      mode
      destinationType
      rowsExported
      rowsDeleted
      bytesWritten
      datasets
      errorMessage
      manifestSummary
    }
  }
`

export const ARCHIVE_COLD_OBJECTS = gql`
  query ArchiveColdObjects($dataset: String, $dayPrefix: String) {
    archiveColdObjects(dataset: $dataset, dayPrefix: $dayPrefix) {
      dataset
      day
      path
      bytes
    }
  }
`

export const REHYDRATE_TOKEN_USAGE_ARCHIVE = gql`
  mutation RehydrateTokenUsageArchive($path: String!) {
    rehydrateTokenUsageArchive(path: $path)
  }
`

export const HOT_DATA_PARTITIONS = gql`
  query HotDataPartitions {
    hotDataPartitions {
      id
      dataset
      periodStart
      periodEnd
      status
      notes
      createdAt
    }
  }
`

export const EVAL_DEFINITIONS = gql`
  query EvalDefinitions {
    evalDefinitions {
      id
      slug
      name
      kind
      enabled
      targetScope
      promptTemplate
      ruleConfig
    }
  }
`

export const EVAL_RUNS = gql`
  query EvalRuns($taskId: ID!) {
    evalRuns(taskId: $taskId) {
      id
      label
      score
      explanation
      status
      triggeredBy
      createdAt
      definition { name slug kind }
    }
  }
`

export const EVAL_SCORECARDS = gql`
  query EvalScorecards($taskId: ID) {
    evalScorecards(taskId: $taskId) {
      id
      score
      notes
      createdBy
      createdAt
    }
  }
`

export const ANNOTATION_QUEUE = gql`
  query AnnotationQueue($status: AnnotationQueueStatus) {
    annotationQueue(status: $status) {
      id
      taskId
      reason
      status
      createdBy
      createdAt
    }
  }
`

export const RUN_TASK_EVALUATIONS = gql`
  mutation RunTaskEvaluations($taskId: ID!) {
    runTaskEvaluations(taskId: $taskId) {
      id
      label
      status
    }
  }
`

export const CREATE_EVAL_SCORECARD = gql`
  mutation CreateEvalScorecard($taskId: ID!, $score: Float!, $notes: String) {
    createEvalScorecard(taskId: $taskId, score: $score, notes: $notes) {
      id
      score
    }
  }
`

export const UPDATE_EVAL_DEFINITION = gql`
  mutation UpdateEvalDefinition($id: ID!, $enabled: Boolean) {
    updateEvalDefinition(id: $id, enabled: $enabled) {
      id
      enabled
    }
  }
`

export const ENQUEUE_ANNOTATION = gql`
  mutation EnqueueAnnotation($taskId: ID!, $reason: AnnotationQueueReason) {
    enqueueAnnotation(taskId: $taskId, reason: $reason) {
      id
      taskId
    }
  }
`

export const COMPLETE_ANNOTATION = gql`
  mutation CompleteAnnotation($id: ID!, $status: AnnotationQueueStatus!, $notes: String) {
    completeAnnotation(id: $id, status: $status, notes: $notes) {
      id
      status
    }
  }
`

export const EVAL_DATASETS = gql`
  query EvalDatasets {
    evalDatasets {
      id
      name
      description
      source
      taskCount
      createdAt
      updatedAt
    }
  }
`

export const EVAL_EXPERIMENTS = gql`
  query EvalExperiments {
    evalExperiments {
      id
      name
      datasetId
      baselineDefinitionId
      variantDefinitionId
      status
      createdAt
      completedAt
      comparison {
        taskCount
        baselinePassRate
        variantPassRate
        baselineAvgScore
        variantAvgScore
      }
    }
  }
`

export const CREATE_EVAL_DATASET = gql`
  mutation CreateEvalDataset($name: String!, $description: String) {
    createEvalDataset(name: $name, description: $description) {
      id
      name
      taskCount
    }
  }
`

export const CREATE_EVAL_EXPERIMENT = gql`
  mutation CreateEvalExperiment(
    $name: String!
    $datasetId: ID!
    $baselineDefinitionId: ID!
    $variantDefinitionId: ID!
  ) {
    createEvalExperiment(
      name: $name
      datasetId: $datasetId
      baselineDefinitionId: $baselineDefinitionId
      variantDefinitionId: $variantDefinitionId
    ) {
      id
      status
    }
  }
`

export const RUN_EVAL_EXPERIMENT = gql`
  mutation RunEvalExperiment($experimentId: ID!) {
    runEvalExperiment(experimentId: $experimentId) {
      id
      status
      comparison {
        baselinePassRate
        variantPassRate
      }
    }
  }
`

export const POPULATE_DATASET_FROM_FAILED = gql`
  mutation PopulateEvalDatasetFromFailedEvals($datasetId: ID!) {
    populateEvalDatasetFromFailedEvals(datasetId: $datasetId) {
      id
      taskCount
    }
  }
`

export const RUN_PLAYGROUND_EVAL = gql`
  mutation RunPlaygroundEval($taskId: ID!, $definitionId: ID!, $promptTemplate: String) {
    runPlaygroundEval(taskId: $taskId, definitionId: $definitionId, promptTemplate: $promptTemplate) {
      label
      score
      explanation
      promptUsed
    }
  }
`

export const TRACE_SPANS = gql`
  query TraceSpans($taskId: ID, $sourceTool: SourceTool, $from: DateTime, $to: DateTime) {
    traceSpans(taskId: $taskId, sourceTool: $sourceTool, from: $from, to: $to) {
      id
      taskId
      name
      kind
      startAt
      durationMs
      status
      sourceTool
      inputRedacted
      outputRedacted
    }
  }
`

export const TOPIC_PATTERNS = gql`
  query TopicPatterns {
    topicPatterns {
      id
      label
      taskCount
      failRate
      relevanceScore
      suspicious
      flagReason
      sampleTaskIds
      periodStart
      periodEnd
      windowDays
      computedAt
    }
  }
`

export const RECOMPUTE_TOPIC_PATTERNS = gql`
  mutation RecomputeTopicPatterns($windowDays: Int) {
    recomputeTopicPatterns(windowDays: $windowDays) {
      id
      label
      taskCount
      failRate
      relevanceScore
      suspicious
      flagReason
      sampleTaskIds
      periodStart
      periodEnd
      windowDays
      computedAt
    }
  }
`

export const SESSION_FEEDBACK = gql`
  query SessionFeedback($taskId: ID!) {
    sessionFeedback(taskId: $taskId) {
      id
      rating
      comment
      feedbackType
      createdBy
      createdAt
    }
  }
`

export const SUBMIT_SESSION_FEEDBACK = gql`
  mutation SubmitSessionFeedback($taskId: ID!, $rating: Int!, $comment: String) {
    submitSessionFeedback(taskId: $taskId, rating: $rating, comment: $comment) {
      id
      rating
      comment
    }
  }
`
