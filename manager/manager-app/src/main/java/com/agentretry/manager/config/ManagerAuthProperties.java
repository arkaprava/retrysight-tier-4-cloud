/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "manager.auth")
public class ManagerAuthProperties {

    private String jwtSecret = "agent-retry-platform-dev-secret-key-32chars";
    private long jwtExpirationHours = 24;
    private long mcpTokenExpirationDays = 30;
    /**
     * Optional bootstrap / sync value for the shared AGENT ingest key (env {@code AGENT_API_KEY}).
     * Empty keeps local-dev seeding of {@code dev-agent-key} when the DB has no keys.
     */
    private String agentApiKey = "";
    /** Dual-key grace window after rotateApiKey (seconds). Previous key remains valid until expiry. */
    private int apiKeyRotationGraceSeconds = 86_400;
    private Secadmin secadmin = new Secadmin();
    private Sso sso = new Sso();

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getJwtExpirationHours() {
        return jwtExpirationHours;
    }

    public void setJwtExpirationHours(long jwtExpirationHours) {
        this.jwtExpirationHours = jwtExpirationHours;
    }

    public long getMcpTokenExpirationDays() {
        return mcpTokenExpirationDays;
    }

    public void setMcpTokenExpirationDays(long mcpTokenExpirationDays) {
        this.mcpTokenExpirationDays = mcpTokenExpirationDays;
    }

    public String getAgentApiKey() {
        return agentApiKey;
    }

    public void setAgentApiKey(String agentApiKey) {
        this.agentApiKey = agentApiKey;
    }

    public int getApiKeyRotationGraceSeconds() {
        return apiKeyRotationGraceSeconds;
    }

    public void setApiKeyRotationGraceSeconds(int apiKeyRotationGraceSeconds) {
        this.apiKeyRotationGraceSeconds = apiKeyRotationGraceSeconds;
    }

    public Secadmin getSecadmin() {
        return secadmin;
    }

    public void setSecadmin(Secadmin secadmin) {
        this.secadmin = secadmin;
    }

    public Sso getSso() {
        return sso;
    }

    public void setSso(Sso sso) {
        this.sso = sso;
    }

    public static class Secadmin {
        private String email = "secadmin@local";
        private String password = "changeme";

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Sso {
        private String callbackBaseUrl = "http://localhost:5173";
        private String secretEncryptionKey = "dev-sso-secret-encryption-key-32";
        private String managerBaseUrl = "http://localhost:28477";

        public String getCallbackBaseUrl() {
            return callbackBaseUrl;
        }

        public void setCallbackBaseUrl(String callbackBaseUrl) {
            this.callbackBaseUrl = callbackBaseUrl;
        }

        public String getSecretEncryptionKey() {
            return secretEncryptionKey;
        }

        public void setSecretEncryptionKey(String secretEncryptionKey) {
            this.secretEncryptionKey = secretEncryptionKey;
        }

        public String getManagerBaseUrl() {
            return managerBaseUrl;
        }

        public void setManagerBaseUrl(String managerBaseUrl) {
            this.managerBaseUrl = managerBaseUrl;
        }
    }
}
