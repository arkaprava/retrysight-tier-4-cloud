/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { gql, useQuery } from '@apollo/client'

export const PLATFORM_FEATURES = gql`
  query PlatformFeatures {
    platformFeatures {
      installationTier
      phase1Foundation
      phase2Collectors
      phase3YamlRegistry
      phase4PushIngest
      costEnabled
      contractCostReviewEnabled
      contractCostApplyEnabled
      reportsEnabled
      mcpEnabled
      sourceToolsEnabled
      pushIngestEnabled
      gitContextCaptureEnabled
      gitContextQueryEnabled
      gitContextAdvancedEnabled
      gitContextEnterpriseEnabled
      retentionPurgeEnabled
      archiveLocalEnabled
      archiveNetworkEnabled
      archiveObjectStorageEnabled
      archiveRestoreEnabled
      rollupAnalyticsEnabled
      partitionMaintenanceEnabled
      clickhouseAnalyticsEnabled
      evaluationsEnabled
      evalExperimentsEnabled
      traceQueryEnabled
      patternsEnabled
    }
  }
`

export const SOURCE_TOOLS = gql`
  query SourceTools {
    sourceTools {
      id
      label
      configType
      minimumTier
      defaultWatchPath
      logFormat
      capabilities
      enabled
    }
  }
`

export type InstallationTier =
  | 'TIER_1_CORE'
  | 'TIER_2_ANALYTICS'
  | 'TIER_3_INTEGRATION'
  | 'TIER_4_CLOUD'

export type PlatformFeaturesData = {
  installationTier: InstallationTier
  phase1Foundation: boolean
  phase2Collectors: boolean
  phase3YamlRegistry: boolean
  phase4PushIngest: boolean
  costEnabled: boolean
  contractCostReviewEnabled: boolean
  contractCostApplyEnabled: boolean
  reportsEnabled: boolean
  mcpEnabled: boolean
  sourceToolsEnabled: boolean
  pushIngestEnabled: boolean
  gitContextCaptureEnabled: boolean
  gitContextQueryEnabled: boolean
  gitContextAdvancedEnabled: boolean
  gitContextEnterpriseEnabled: boolean
  retentionPurgeEnabled: boolean
  archiveLocalEnabled: boolean
  archiveNetworkEnabled: boolean
  archiveObjectStorageEnabled: boolean
  archiveRestoreEnabled: boolean
  rollupAnalyticsEnabled: boolean
  partitionMaintenanceEnabled: boolean
  clickhouseAnalyticsEnabled: boolean
  evaluationsEnabled: boolean
  evalExperimentsEnabled: boolean
  traceQueryEnabled: boolean
  patternsEnabled: boolean
}

export type SourceToolInfo = {
  id: string
  label: string
  configType: string
  minimumTier: InstallationTier
  defaultWatchPath: string
  logFormat: string
  capabilities: string[]
  enabled: boolean
}

export function usePlatformFeatures() {
  const { data, loading } = useQuery<{ platformFeatures: PlatformFeaturesData }>(PLATFORM_FEATURES)
  const features = data?.platformFeatures
  return {
    loading,
    featuresLoading: loading,
    features,
    costEnabled: features?.costEnabled ?? false,
    contractCostReviewEnabled: features?.contractCostReviewEnabled ?? false,
    contractCostApplyEnabled: features?.contractCostApplyEnabled ?? false,
    reportsEnabled: features?.reportsEnabled ?? false,
    mcpEnabled: features?.mcpEnabled ?? false,
    sourceToolsEnabled: features?.sourceToolsEnabled ?? false,
    gitContextCaptureEnabled: features?.gitContextCaptureEnabled ?? false,
    gitContextQueryEnabled: features?.gitContextQueryEnabled ?? false,
    gitContextAdvancedEnabled: features?.gitContextAdvancedEnabled ?? false,
    gitContextEnterpriseEnabled: features?.gitContextEnterpriseEnabled ?? false,
    retentionPurgeEnabled: features?.retentionPurgeEnabled ?? false,
    archiveLocalEnabled: features?.archiveLocalEnabled ?? false,
    archiveNetworkEnabled: features?.archiveNetworkEnabled ?? false,
    archiveObjectStorageEnabled: features?.archiveObjectStorageEnabled ?? false,
    archiveRestoreEnabled: features?.archiveRestoreEnabled ?? false,
    rollupAnalyticsEnabled: features?.rollupAnalyticsEnabled ?? false,
    partitionMaintenanceEnabled: features?.partitionMaintenanceEnabled ?? false,
    clickhouseAnalyticsEnabled: features?.clickhouseAnalyticsEnabled ?? false,
    evaluationsEnabled: features?.evaluationsEnabled ?? false,
    evalExperimentsEnabled: features?.evalExperimentsEnabled ?? false,
    traceQueryEnabled: features?.traceQueryEnabled ?? false,
    patternsEnabled: features?.patternsEnabled ?? false,
  }
}

export function useSourceTools() {
  const { data, loading } = useQuery<{ sourceTools: SourceToolInfo[] }>(SOURCE_TOOLS, {
    skip: false,
  })
  return {
    loading,
    sourceTools: data?.sourceTools ?? [],
    enabledSourceTools: (data?.sourceTools ?? []).filter((tool) => tool.enabled),
  }
}

export function formatSourceTool(tool: string, sourceTools?: SourceToolInfo[]): string {
  const match = sourceTools?.find((entry) => entry.id === tool)
  if (match) {
    return match.label
  }
  return tool.replace(/_/g, ' ')
}
