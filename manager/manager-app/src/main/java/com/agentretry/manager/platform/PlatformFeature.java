/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.platform;

import com.agentretry.contract.InstallationTier;

public enum PlatformFeature {
    PHASE1_FOUNDATION(InstallationTier.TIER_1_CORE, true),
    PHASE2_COLLECTORS(InstallationTier.TIER_2_ANALYTICS, true),
    PHASE3_YAML_REGISTRY(InstallationTier.TIER_3_INTEGRATION, true),
    PHASE4_PUSH_INGEST(InstallationTier.TIER_4_CLOUD, true),
    COST_ANALYTICS(InstallationTier.TIER_2_ANALYTICS, false),
    CONTRACT_COST_REVIEW(InstallationTier.TIER_2_ANALYTICS, false),
    CONTRACT_COST_APPLY(InstallationTier.TIER_3_INTEGRATION, false),
    REPORTS(InstallationTier.TIER_2_ANALYTICS, false),
    MCP_INTEGRATION(InstallationTier.TIER_3_INTEGRATION, false),
    SOURCE_TOOLS_API(InstallationTier.TIER_3_INTEGRATION, false),
    GIT_CONTEXT_CAPTURE(InstallationTier.TIER_2_ANALYTICS, true),
    GIT_CONTEXT_QUERY(InstallationTier.TIER_3_INTEGRATION, true),
    GIT_CONTEXT_ADVANCED(InstallationTier.TIER_3_INTEGRATION, true),
    GIT_CONTEXT_ENTERPRISE(InstallationTier.TIER_4_CLOUD, true),
    RETENTION_PURGE(InstallationTier.TIER_1_CORE, false),
    ARCHIVE_LOCAL(InstallationTier.TIER_2_ANALYTICS, false),
    ARCHIVE_NETWORK(InstallationTier.TIER_3_INTEGRATION, false),
    ARCHIVE_OBJECT_STORAGE(InstallationTier.TIER_4_CLOUD, false),
    ARCHIVE_RESTORE_QUERY(InstallationTier.TIER_4_CLOUD, false),
    ROLLUP_ANALYTICS(InstallationTier.TIER_2_ANALYTICS, false),
    PARTITION_MAINTENANCE(InstallationTier.TIER_3_INTEGRATION, false),
    /** Optional ClickHouse / warehouse analytics dual-write + reads. */
    CLICKHOUSE_ANALYTICS(InstallationTier.TIER_3_INTEGRATION, false),
    /** Automated and LLM-as-judge session evaluations. */
    EVALUATIONS(InstallationTier.TIER_2_ANALYTICS, false),
    /** Dataset curation and offline experiment comparison. */
    EVAL_EXPERIMENTS(InstallationTier.TIER_3_INTEGRATION, false),
    /** OTel span store and trace search. */
    TRACE_QUERY(InstallationTier.TIER_3_INTEGRATION, false),
    /** Topic pattern clustering over production sessions. */
    PATTERNS(InstallationTier.TIER_3_INTEGRATION, false);

    private final InstallationTier minimumTier;
    private final boolean phaseFlag;

    PlatformFeature(InstallationTier minimumTier, boolean phaseFlag) {
        this.minimumTier = minimumTier;
        this.phaseFlag = phaseFlag;
    }

    public InstallationTier minimumTier() {
        return minimumTier;
    }

    public boolean phaseFlag() {
        return phaseFlag;
    }
}
