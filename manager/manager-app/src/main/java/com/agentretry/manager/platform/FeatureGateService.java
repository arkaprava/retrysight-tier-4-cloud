/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.platform;

import com.agentretry.contract.InstallationTier;
import com.agentretry.contract.SourceToolCatalog;
import com.agentretry.contract.SourceToolDescriptor;
import com.agentretry.manager.config.AnalyticsProperties;
import com.agentretry.manager.config.PlatformProperties;
import com.agentretry.manager.graphql.PlatformFeatures;
import com.agentretry.manager.graphql.SourceToolInfo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FeatureGateService {

    private final PlatformProperties properties;
    private final AnalyticsProperties analyticsProperties;

    public FeatureGateService(PlatformProperties properties, AnalyticsProperties analyticsProperties) {
        this.properties = properties;
        this.analyticsProperties = analyticsProperties;
    }

    public InstallationTier installationTier() {
        return properties.getInstallationTier();
    }

    public boolean isEnabled(PlatformFeature feature) {
        if (!properties.getInstallationTier().includes(feature.minimumTier())) {
            return false;
        }
        if (!feature.phaseFlag()) {
            return true;
        }
        return switch (feature) {
            case PHASE1_FOUNDATION -> properties.getFeatures().isPhase1Foundation();
            case PHASE2_COLLECTORS -> properties.getFeatures().isPhase2Collectors();
            case PHASE3_YAML_REGISTRY -> properties.getFeatures().isPhase3YamlRegistry();
            case PHASE4_PUSH_INGEST -> properties.getFeatures().isPhase4PushIngest();
            case GIT_CONTEXT_CAPTURE -> properties.getFeatures().isGitContextCapture();
            case GIT_CONTEXT_QUERY -> properties.getFeatures().isGitContextQuery();
            case GIT_CONTEXT_ADVANCED -> properties.getFeatures().isGitContextAdvanced();
            case GIT_CONTEXT_ENTERPRISE -> properties.getFeatures().isGitContextEnterprise();
            default -> true;
        };
    }

    public boolean isCostEnabled() {
        return isEnabled(PlatformFeature.COST_ANALYTICS) && isEnabled(PlatformFeature.PHASE2_COLLECTORS);
    }

    /**
     * Tier 2+: upload, parse, review, and dual-approve contract cost units.
     */
    public boolean isContractCostReviewEnabled() {
        return isCostEnabled() && isEnabled(PlatformFeature.CONTRACT_COST_REVIEW);
    }

    /**
     * Tier 3+: apply dual-approved cost units to live pricing and billing.
     */
    public boolean isContractCostApplyEnabled() {
        return isContractCostReviewEnabled() && isEnabled(PlatformFeature.CONTRACT_COST_APPLY);
    }

    public boolean isReportsEnabled() {
        return isEnabled(PlatformFeature.REPORTS) && isEnabled(PlatformFeature.PHASE2_COLLECTORS);
    }

    public boolean isMcpEnabled() {
        return isEnabled(PlatformFeature.MCP_INTEGRATION) && isEnabled(PlatformFeature.PHASE3_YAML_REGISTRY);
    }

    public boolean isSourceToolsEnabled() {
        return isEnabled(PlatformFeature.SOURCE_TOOLS_API) && isEnabled(PlatformFeature.PHASE3_YAML_REGISTRY);
    }

    public boolean isPushIngestEnabled() {
        return isEnabled(PlatformFeature.PHASE4_PUSH_INGEST);
    }

    public boolean isGitContextCaptureEnabled() {
        return isEnabled(PlatformFeature.GIT_CONTEXT_CAPTURE);
    }

    public boolean isGitContextQueryEnabled() {
        return isEnabled(PlatformFeature.GIT_CONTEXT_QUERY);
    }

    public boolean isGitContextAdvancedEnabled() {
        return isEnabled(PlatformFeature.GIT_CONTEXT_ADVANCED);
    }

    public boolean isGitContextEnterpriseEnabled() {
        return isEnabled(PlatformFeature.GIT_CONTEXT_ENTERPRISE);
    }

    public boolean isClickHouseAnalyticsEnabled() {
        return isEnabled(PlatformFeature.CLICKHOUSE_ANALYTICS)
                && analyticsProperties.getClickhouse().isEnabled();
    }

    public boolean isEvaluationsEnabled() {
        return isEnabled(PlatformFeature.EVALUATIONS);
    }

    public boolean isEvalExperimentsEnabled() {
        return isEvaluationsEnabled() && isEnabled(PlatformFeature.EVAL_EXPERIMENTS);
    }

    public boolean isTraceQueryEnabled() {
        return isEnabled(PlatformFeature.TRACE_QUERY);
    }

    public boolean isPatternsEnabled() {
        return isEvaluationsEnabled() && isEnabled(PlatformFeature.PATTERNS);
    }

    public boolean shouldRedactProjectRoot() {
        return isGitContextEnterpriseEnabled() && properties.getPrivacy().isRedactProjectRoot();
    }

    public PlatformFeatures snapshot() {
        return new PlatformFeatures(
                properties.getInstallationTier(),
                isEnabled(PlatformFeature.PHASE1_FOUNDATION),
                isEnabled(PlatformFeature.PHASE2_COLLECTORS),
                isEnabled(PlatformFeature.PHASE3_YAML_REGISTRY),
                isEnabled(PlatformFeature.PHASE4_PUSH_INGEST),
                isCostEnabled(),
                isContractCostReviewEnabled(),
                isContractCostApplyEnabled(),
                isReportsEnabled(),
                isMcpEnabled(),
                isSourceToolsEnabled(),
                isPushIngestEnabled(),
                isGitContextCaptureEnabled(),
                isGitContextQueryEnabled(),
                isGitContextAdvancedEnabled(),
                isGitContextEnterpriseEnabled(),
                isEnabled(PlatformFeature.RETENTION_PURGE),
                isEnabled(PlatformFeature.ARCHIVE_LOCAL),
                isEnabled(PlatformFeature.ARCHIVE_NETWORK),
                isEnabled(PlatformFeature.ARCHIVE_OBJECT_STORAGE),
                isEnabled(PlatformFeature.ARCHIVE_RESTORE_QUERY),
                isEnabled(PlatformFeature.ROLLUP_ANALYTICS),
                isEnabled(PlatformFeature.PARTITION_MAINTENANCE),
                isClickHouseAnalyticsEnabled(),
                isEvaluationsEnabled(),
                isEvalExperimentsEnabled(),
                isTraceQueryEnabled(),
                isPatternsEnabled()
        );
    }

    public List<SourceToolInfo> availableSourceTools() {
        InstallationTier tier = properties.getInstallationTier();
        boolean foundationEnabled = isEnabled(PlatformFeature.PHASE1_FOUNDATION);
        boolean extendedCollectorsEnabled = isEnabled(PlatformFeature.PHASE2_COLLECTORS);
        return SourceToolCatalog.all().stream()
                .map(descriptor -> {
                    boolean enabled = foundationEnabled && descriptor.availableAt(tier);
                    if (descriptor.minimumTier().level() >= InstallationTier.TIER_2_ANALYTICS.level()) {
                        enabled = enabled && extendedCollectorsEnabled;
                    }
                    return toInfo(descriptor, enabled);
                })
                .toList();
    }

    private SourceToolInfo toInfo(SourceToolDescriptor descriptor, boolean enabled) {
        return new SourceToolInfo(
                descriptor.id(),
                descriptor.label(),
                descriptor.configType(),
                descriptor.minimumTier(),
                descriptor.defaultWatchPath(),
                descriptor.logFormat().name(),
                descriptor.capabilities(),
                enabled
        );
    }
}
