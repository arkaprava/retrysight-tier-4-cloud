/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import com.agentretry.contract.InstallationTier;

public record PlatformFeatures(
        InstallationTier installationTier,
        boolean phase1Foundation,
        boolean phase2Collectors,
        boolean phase3YamlRegistry,
        boolean phase4PushIngest,
        boolean costEnabled,
        boolean contractCostReviewEnabled,
        boolean contractCostApplyEnabled,
        boolean reportsEnabled,
        boolean mcpEnabled,
        boolean sourceToolsEnabled,
        boolean pushIngestEnabled,
        boolean gitContextCaptureEnabled,
        boolean gitContextQueryEnabled,
        boolean gitContextAdvancedEnabled,
        boolean gitContextEnterpriseEnabled,
        boolean retentionPurgeEnabled,
        boolean archiveLocalEnabled,
        boolean archiveNetworkEnabled,
        boolean archiveObjectStorageEnabled,
        boolean archiveRestoreEnabled,
        boolean rollupAnalyticsEnabled,
        boolean partitionMaintenanceEnabled,
        boolean clickhouseAnalyticsEnabled,
        boolean evaluationsEnabled,
        boolean evalExperimentsEnabled,
        boolean traceQueryEnabled,
        boolean patternsEnabled
) {
}
