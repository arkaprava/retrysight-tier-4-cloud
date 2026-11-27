/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "manager.security")
public class ManagerSecurityProperties {

    /** When false, GraphiQL and vendor assets are not anonymously accessible. */
    private boolean graphiqlEnabled = true;
    /** When true, all GraphQL HTTP methods require a UI/ADMIN/SECADMIN JWT. */
    private boolean requireGraphqlAuth = false;
    private RateLimit rateLimit = new RateLimit();

    public boolean isGraphiqlEnabled() {
        return graphiqlEnabled;
    }

    public void setGraphiqlEnabled(boolean graphiqlEnabled) {
        this.graphiqlEnabled = graphiqlEnabled;
    }

    public boolean isRequireGraphqlAuth() {
        return requireGraphqlAuth;
    }

    public void setRequireGraphqlAuth(boolean requireGraphqlAuth) {
        this.requireGraphqlAuth = requireGraphqlAuth;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public static class RateLimit {
        private int loginFailuresPerIp = 10;
        private int loginWindowSeconds = 900;
        private int ingestRequestsPerKey = 100;
        private int ingestWindowSeconds = 60;
        private int mcpTokenRequestsPerIp = 10;

        public int getLoginFailuresPerIp() {
            return loginFailuresPerIp;
        }

        public void setLoginFailuresPerIp(int loginFailuresPerIp) {
            this.loginFailuresPerIp = loginFailuresPerIp;
        }

        public int getLoginWindowSeconds() {
            return loginWindowSeconds;
        }

        public void setLoginWindowSeconds(int loginWindowSeconds) {
            this.loginWindowSeconds = loginWindowSeconds;
        }

        public int getIngestRequestsPerKey() {
            return ingestRequestsPerKey;
        }

        public void setIngestRequestsPerKey(int ingestRequestsPerKey) {
            this.ingestRequestsPerKey = ingestRequestsPerKey;
        }

        public int getIngestWindowSeconds() {
            return ingestWindowSeconds;
        }

        public void setIngestWindowSeconds(int ingestWindowSeconds) {
            this.ingestWindowSeconds = ingestWindowSeconds;
        }

        public int getMcpTokenRequestsPerIp() {
            return mcpTokenRequestsPerIp;
        }

        public void setMcpTokenRequestsPerIp(int mcpTokenRequestsPerIp) {
            this.mcpTokenRequestsPerIp = mcpTokenRequestsPerIp;
        }
    }
}
