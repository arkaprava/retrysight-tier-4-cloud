/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.config;

import com.agentretry.contract.InstallationTier;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "manager.platform")
public class PlatformProperties {

    private InstallationTier installationTier = InstallationTier.TIER_4_CLOUD;
    private FeatureFlags features = new FeatureFlags();
    private Privacy privacy = new Privacy();

    public InstallationTier getInstallationTier() {
        return installationTier;
    }

    public void setInstallationTier(InstallationTier installationTier) {
        this.installationTier = installationTier;
    }

    public FeatureFlags getFeatures() {
        return features;
    }

    public void setFeatures(FeatureFlags features) {
        this.features = features;
    }

    public Privacy getPrivacy() {
        return privacy;
    }

    public void setPrivacy(Privacy privacy) {
        this.privacy = privacy;
    }

    public static class FeatureFlags {
        private boolean phase1Foundation = true;
        private boolean phase2Collectors = true;
        private boolean phase3YamlRegistry = true;
        private boolean phase4PushIngest = false;
        private boolean gitContextCapture = true;
        private boolean gitContextQuery = true;
        private boolean gitContextAdvanced = true;
        private boolean gitContextEnterprise = true;

        public boolean isPhase1Foundation() {
            return phase1Foundation;
        }

        public void setPhase1Foundation(boolean phase1Foundation) {
            this.phase1Foundation = phase1Foundation;
        }

        public boolean isPhase2Collectors() {
            return phase2Collectors;
        }

        public void setPhase2Collectors(boolean phase2Collectors) {
            this.phase2Collectors = phase2Collectors;
        }

        public boolean isPhase3YamlRegistry() {
            return phase3YamlRegistry;
        }

        public void setPhase3YamlRegistry(boolean phase3YamlRegistry) {
            this.phase3YamlRegistry = phase3YamlRegistry;
        }

        public boolean isPhase4PushIngest() {
            return phase4PushIngest;
        }

        public void setPhase4PushIngest(boolean phase4PushIngest) {
            this.phase4PushIngest = phase4PushIngest;
        }

        public boolean isGitContextCapture() {
            return gitContextCapture;
        }

        public void setGitContextCapture(boolean gitContextCapture) {
            this.gitContextCapture = gitContextCapture;
        }

        public boolean isGitContextQuery() {
            return gitContextQuery;
        }

        public void setGitContextQuery(boolean gitContextQuery) {
            this.gitContextQuery = gitContextQuery;
        }

        public boolean isGitContextAdvanced() {
            return gitContextAdvanced;
        }

        public void setGitContextAdvanced(boolean gitContextAdvanced) {
            this.gitContextAdvanced = gitContextAdvanced;
        }

        public boolean isGitContextEnterprise() {
            return gitContextEnterprise;
        }

        public void setGitContextEnterprise(boolean gitContextEnterprise) {
            this.gitContextEnterprise = gitContextEnterprise;
        }
    }

    public static class Privacy {
        private boolean redactProjectRoot = false;
        private boolean storeFullPrompts = false;

        public boolean isRedactProjectRoot() {
            return redactProjectRoot;
        }

        public void setRedactProjectRoot(boolean redactProjectRoot) {
            this.redactProjectRoot = redactProjectRoot;
        }

        public boolean isStoreFullPrompts() {
            return storeFullPrompts;
        }

        public void setStoreFullPrompts(boolean storeFullPrompts) {
            this.storeFullPrompts = storeFullPrompts;
        }
    }
}
