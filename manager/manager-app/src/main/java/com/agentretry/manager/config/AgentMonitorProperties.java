/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "manager.agents")
public class AgentMonitorProperties {

    /** Agents with lastHeartbeatAt older than this many seconds are considered stale/offline. */
    private int staleAfterSeconds = 120;

    public int getStaleAfterSeconds() {
        return staleAfterSeconds;
    }

    public void setStaleAfterSeconds(int staleAfterSeconds) {
        this.staleAfterSeconds = staleAfterSeconds;
    }
}
