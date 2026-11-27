/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.platform;

import com.agentretry.contract.InstallationTier;
import com.agentretry.manager.config.AnalyticsProperties;
import com.agentretry.manager.config.PlatformProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureGateServiceTest {

    @Test
    void tierOneDisablesCostReportsAndMcp() {
        FeatureGateService service = service(InstallationTier.TIER_1_CORE, flags(true, true, true, false));

        assertTrue(service.isEnabled(PlatformFeature.PHASE1_FOUNDATION));
        assertFalse(service.isCostEnabled());
        assertFalse(service.isContractCostReviewEnabled());
        assertFalse(service.isContractCostApplyEnabled());
        assertFalse(service.isReportsEnabled());
        assertFalse(service.isMcpEnabled());
        assertFalse(service.isPushIngestEnabled());
        assertTrue(service.availableSourceTools().stream()
                .anyMatch(tool -> tool.id().name().equals("CURSOR") && tool.enabled()));
        assertFalse(service.availableSourceTools().stream()
                .anyMatch(tool -> tool.id().name().equals("WINDSURF") && tool.enabled()));
    }

    @Test
    void tierTwoEnablesContractReviewButNotApply() {
        FeatureGateService service = service(InstallationTier.TIER_2_ANALYTICS, flags(true, true, true, false));

        assertTrue(service.isCostEnabled());
        assertTrue(service.isContractCostReviewEnabled());
        assertFalse(service.isContractCostApplyEnabled());
    }

    @Test
    void tierThreeEnablesContractReviewAndApply() {
        FeatureGateService service = service(InstallationTier.TIER_3_INTEGRATION, flags(true, true, true, false));

        assertTrue(service.isContractCostReviewEnabled());
        assertTrue(service.isContractCostApplyEnabled());
    }

    @Test
    void tierFourWithPhaseFourFlagEnablesPushIngest() {
        FeatureGateService service = service(InstallationTier.TIER_4_CLOUD, flags(true, true, true, true));

        assertTrue(service.isMcpEnabled());
        assertTrue(service.isPushIngestEnabled());
        assertTrue(service.availableSourceTools().stream()
                .anyMatch(tool -> tool.id().name().equals("WINDSURF") && tool.enabled()));
    }

    @Test
    void featureFlagCanDisablePhaseTwoWithinTier() {
        FeatureGateService service = service(InstallationTier.TIER_4_CLOUD, flags(true, false, true, false));

        assertFalse(service.isCostEnabled());
        assertFalse(service.availableSourceTools().stream()
                .anyMatch(tool -> tool.id().name().equals("KIRO") && tool.enabled()));
    }

    @Test
    void tierOneDisablesGitContext() {
        FeatureGateService service = service(InstallationTier.TIER_1_CORE, gitFlags(true, true, true, true));

        assertFalse(service.isGitContextCaptureEnabled());
        assertFalse(service.isGitContextQueryEnabled());
        assertFalse(service.isGitContextAdvancedEnabled());
        assertFalse(service.isGitContextEnterpriseEnabled());
    }

    @Test
    void tierTwoEnablesCaptureOnly() {
        FeatureGateService service = service(InstallationTier.TIER_2_ANALYTICS, gitFlags(true, true, true, true));

        assertTrue(service.isGitContextCaptureEnabled());
        assertFalse(service.isGitContextQueryEnabled());
        assertFalse(service.isGitContextAdvancedEnabled());
        assertFalse(service.isGitContextEnterpriseEnabled());
    }

    @Test
    void tierThreeEnablesCaptureQueryAndAdvanced() {
        FeatureGateService service = service(InstallationTier.TIER_3_INTEGRATION, gitFlags(true, true, true, true));

        assertTrue(service.isGitContextCaptureEnabled());
        assertTrue(service.isGitContextQueryEnabled());
        assertTrue(service.isGitContextAdvancedEnabled());
        assertFalse(service.isGitContextEnterpriseEnabled());
    }

    @Test
    void tierFourEnablesAllGitFeatures() {
        FeatureGateService service = service(InstallationTier.TIER_4_CLOUD, gitFlags(true, true, true, true));

        assertTrue(service.isGitContextCaptureEnabled());
        assertTrue(service.isGitContextQueryEnabled());
        assertTrue(service.isGitContextAdvancedEnabled());
        assertTrue(service.isGitContextEnterpriseEnabled());
    }

    @Test
    void gitCaptureFlagCanDisableWithinTier() {
        FeatureGateService service = service(InstallationTier.TIER_4_CLOUD, gitFlags(false, true, true, true));

        assertFalse(service.isGitContextCaptureEnabled());
        assertTrue(service.isGitContextQueryEnabled());
    }

    private FeatureGateService service(InstallationTier tier, PlatformProperties.FeatureFlags flags) {
        PlatformProperties properties = new PlatformProperties();
        properties.setInstallationTier(tier);
        properties.setFeatures(flags);
        return new FeatureGateService(properties, new AnalyticsProperties());
    }

    private PlatformProperties.FeatureFlags flags(
            boolean phase1,
            boolean phase2,
            boolean phase3,
            boolean phase4
    ) {
        PlatformProperties.FeatureFlags flags = gitFlags(true, true, true, true);
        flags.setPhase1Foundation(phase1);
        flags.setPhase2Collectors(phase2);
        flags.setPhase3YamlRegistry(phase3);
        flags.setPhase4PushIngest(phase4);
        return flags;
    }

    private PlatformProperties.FeatureFlags gitFlags(
            boolean capture,
            boolean query,
            boolean advanced,
            boolean enterprise
    ) {
        PlatformProperties.FeatureFlags flags = new PlatformProperties.FeatureFlags();
        flags.setGitContextCapture(capture);
        flags.setGitContextQuery(query);
        flags.setGitContextAdvanced(advanced);
        flags.setGitContextEnterprise(enterprise);
        return flags;
    }
}
