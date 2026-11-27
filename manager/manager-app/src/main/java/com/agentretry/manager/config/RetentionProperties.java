/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "manager.retention")
public class RetentionProperties {
    private boolean enabled = true;
    private int taskEventsDays = 90;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getTaskEventsDays() { return taskEventsDays; }
    public void setTaskEventsDays(int taskEventsDays) { this.taskEventsDays = taskEventsDays; }
}
