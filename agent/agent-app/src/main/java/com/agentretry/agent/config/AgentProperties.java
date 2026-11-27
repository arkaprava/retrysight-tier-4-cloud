/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.config;

import com.agentretry.contract.InstallationTier;
import com.agentretry.contract.SourceToolDescriptor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private Manager manager = new Manager();
    private Upload upload = new Upload();
    private Identity identity = new Identity();
    private Privacy privacy = new Privacy();
    private User user = new User();
    private Platform platform = new Platform();
    private Discovery discovery = new Discovery();
    private Session session = new Session();
    private Cli cli = new Cli();
    private List<CollectorConfig> collectors = new ArrayList<>();

    public Manager getManager() {
        return manager;
    }

    public void setManager(Manager manager) {
        this.manager = manager;
    }

    public Upload getUpload() {
        return upload;
    }

    public void setUpload(Upload upload) {
        this.upload = upload;
    }

    public Identity getIdentity() {
        return identity;
    }

    public void setIdentity(Identity identity) {
        this.identity = identity;
    }

    public Privacy getPrivacy() {
        return privacy;
    }

    public void setPrivacy(Privacy privacy) {
        this.privacy = privacy;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Platform getPlatform() {
        return platform;
    }

    public void setPlatform(Platform platform) {
        this.platform = platform;
    }

    public Discovery getDiscovery() {
        return discovery;
    }

    public void setDiscovery(Discovery discovery) {
        this.discovery = discovery;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public Cli getCli() {
        return cli;
    }

    public void setCli(Cli cli) {
        this.cli = cli;
    }

    public List<CollectorConfig> getCollectors() {
        return collectors;
    }

    public void setCollectors(List<CollectorConfig> collectors) {
        this.collectors = collectors;
    }

    public boolean isCollectorAvailable(String configType) {
        if (!platform.isPhase1Foundation()) {
            return false;
        }
        Optional<SourceToolDescriptor> descriptor = SourceToolDescriptor.findByConfigType(configType);
        if (descriptor.isEmpty()) {
            return platform.isPhase3YamlRegistry();
        }
        if (!platform.getInstallationTier().includes(descriptor.get().minimumTier())) {
            return false;
        }
        if (descriptor.get().minimumTier().level() >= InstallationTier.TIER_2_ANALYTICS.level()) {
            return platform.isPhase2Collectors();
        }
        return true;
    }

    public boolean isGitContextCaptureEnabled() {
        return platform.isGitContextCaptureEnabled();
    }

    public boolean isGitContextAdvancedEnabled() {
        return platform.isGitContextAdvancedEnabled();
    }

    public boolean isGitContextEnterpriseEnabled() {
        return platform.isGitContextEnterpriseEnabled();
    }

    public static class Platform {
        private InstallationTier installationTier = InstallationTier.TIER_4_CLOUD;
        private boolean autoDiscovery = true;
        private FeatureFlags features = new FeatureFlags();

        public InstallationTier getInstallationTier() {
            return installationTier;
        }

        public void setInstallationTier(InstallationTier installationTier) {
            this.installationTier = installationTier;
        }

        public boolean isAutoDiscovery() {
            return autoDiscovery;
        }

        public void setAutoDiscovery(boolean autoDiscovery) {
            this.autoDiscovery = autoDiscovery;
        }

        public FeatureFlags getFeatures() {
            return features;
        }

        public void setFeatures(FeatureFlags features) {
            this.features = features;
        }

        public boolean isPhase1Foundation() {
            return features.isPhase1Foundation();
        }

        public boolean isPhase2Collectors() {
            return features.isPhase2Collectors()
                    && installationTier.includes(InstallationTier.TIER_2_ANALYTICS);
        }

        public boolean isPhase3YamlRegistry() {
            return features.isPhase3YamlRegistry()
                    && installationTier.includes(InstallationTier.TIER_3_INTEGRATION);
        }

        public boolean isPhase4PushIngest() {
            return features.isPhase4PushIngest()
                    && installationTier.includes(InstallationTier.TIER_4_CLOUD);
        }

        public boolean isGitContextCaptureEnabled() {
            return features.isGitContextCapture()
                    && installationTier.includes(InstallationTier.TIER_2_ANALYTICS);
        }

        public boolean isGitContextQueryEnabled() {
            return features.isGitContextQuery()
                    && installationTier.includes(InstallationTier.TIER_3_INTEGRATION);
        }

        public boolean isGitContextAdvancedEnabled() {
            return features.isGitContextAdvanced()
                    && installationTier.includes(InstallationTier.TIER_3_INTEGRATION);
        }

        public boolean isGitContextEnterpriseEnabled() {
            return features.isGitContextEnterprise()
                    && installationTier.includes(InstallationTier.TIER_4_CLOUD);
        }
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

    public static class Manager {
        private String url = "http://localhost:28477";
        private String apiKey = "dev-agent-key";
        /** Optional explicit HTTP proxy host (overrides HTTP_PROXY / HTTPS_PROXY when set). */
        private String proxyHost;
        private Integer proxyPort;
        /** Optional username for authenticated proxies. */
        private String proxyUsername;
        private String proxyPassword;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getProxyHost() {
            return proxyHost;
        }

        public void setProxyHost(String proxyHost) {
            this.proxyHost = proxyHost;
        }

        public Integer getProxyPort() {
            return proxyPort;
        }

        public void setProxyPort(Integer proxyPort) {
            this.proxyPort = proxyPort;
        }

        public String getProxyUsername() {
            return proxyUsername;
        }

        public void setProxyUsername(String proxyUsername) {
            this.proxyUsername = proxyUsername;
        }

        public String getProxyPassword() {
            return proxyPassword;
        }

        public void setProxyPassword(String proxyPassword) {
            this.proxyPassword = proxyPassword;
        }
    }

    public static class Identity {
        private String idFile = "${user.home}/.agent-retry/agent-id";
        private String lockFile = "${user.home}/.agent-retry/agent.lock";

        public String getIdFile() {
            return idFile;
        }

        public void setIdFile(String idFile) {
            this.idFile = idFile;
        }

        public String getLockFile() {
            return lockFile;
        }

        public void setLockFile(String lockFile) {
            this.lockFile = lockFile;
        }
    }

    public static class Discovery {
        private int recencyDays = 30;
        private int refreshIntervalSeconds = 300;

        public int getRecencyDays() {
            return recencyDays;
        }

        public void setRecencyDays(int recencyDays) {
            this.recencyDays = recencyDays;
        }

        public int getRefreshIntervalSeconds() {
            return refreshIntervalSeconds;
        }

        public void setRefreshIntervalSeconds(int refreshIntervalSeconds) {
            this.refreshIntervalSeconds = refreshIntervalSeconds;
        }
    }

    public static class Session {
        private int idleCompleteMinutes = 30;

        public int getIdleCompleteMinutes() {
            return idleCompleteMinutes;
        }

        public void setIdleCompleteMinutes(int idleCompleteMinutes) {
            this.idleCompleteMinutes = idleCompleteMinutes;
        }
    }

    public static class Cli {
        private String command = "run";

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public boolean isDiscover() {
            return "discover".equalsIgnoreCase(command);
        }
    }

    public enum DiscoveryMode {
        AUTO,
        FIXED,
        SUPPLEMENT
    }

    public static class Upload {
        private int batchIntervalSeconds = 2;
        private int scanIntervalSeconds = 3;
        private int maxBatchSize = 500;
        private String bufferPath = "${user.home}/.agent-retry/buffer.db";
        /** Drop oldest rows when pending count exceeds this (0 = disabled). */
        private int bufferMaxRows = 50_000;
        /** Drop rows older than this many hours (0 = disabled). */
        private int bufferMaxAgeHours = 168;
        /** Drop oldest rows when SQLite file exceeds this many bytes (0 = disabled). */
        private long bufferMaxBytes = 268_435_456L;

        public int getBatchIntervalSeconds() {
            return batchIntervalSeconds;
        }

        public void setBatchIntervalSeconds(int batchIntervalSeconds) {
            this.batchIntervalSeconds = batchIntervalSeconds;
        }

        public int getScanIntervalSeconds() {
            return scanIntervalSeconds;
        }

        public void setScanIntervalSeconds(int scanIntervalSeconds) {
            this.scanIntervalSeconds = scanIntervalSeconds;
        }

        public int getMaxBatchSize() {
            return maxBatchSize;
        }

        public void setMaxBatchSize(int maxBatchSize) {
            this.maxBatchSize = maxBatchSize;
        }

        public String getBufferPath() {
            return bufferPath;
        }

        public void setBufferPath(String bufferPath) {
            this.bufferPath = bufferPath;
        }

        public int getBufferMaxRows() {
            return bufferMaxRows;
        }

        public void setBufferMaxRows(int bufferMaxRows) {
            this.bufferMaxRows = bufferMaxRows;
        }

        public int getBufferMaxAgeHours() {
            return bufferMaxAgeHours;
        }

        public void setBufferMaxAgeHours(int bufferMaxAgeHours) {
            this.bufferMaxAgeHours = bufferMaxAgeHours;
        }

        public long getBufferMaxBytes() {
            return bufferMaxBytes;
        }

        public void setBufferMaxBytes(long bufferMaxBytes) {
            this.bufferMaxBytes = bufferMaxBytes;
        }
    }

    public static class User {
        private String email;
        private String displayName;
        private String osUsername;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getOsUsername() {
            return osUsername;
        }

        public void setOsUsername(String osUsername) {
            this.osUsername = osUsername;
        }
    }

    public static class Privacy {
        private boolean storeFullPrompts = false;

        public boolean isStoreFullPrompts() {
            return storeFullPrompts;
        }

        public void setStoreFullPrompts(boolean storeFullPrompts) {
            this.storeFullPrompts = storeFullPrompts;
        }
    }

    public static class CollectorConfig {
        private String type;
        private boolean enabled = true;
        private String watchPath;
        private DiscoveryMode discoveryMode = DiscoveryMode.AUTO;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getWatchPath() {
            return watchPath;
        }

        public void setWatchPath(String watchPath) {
            this.watchPath = watchPath;
        }

        public DiscoveryMode getDiscoveryMode() {
            return discoveryMode;
        }

        public void setDiscoveryMode(DiscoveryMode discoveryMode) {
            this.discoveryMode = discoveryMode;
        }
    }
}
