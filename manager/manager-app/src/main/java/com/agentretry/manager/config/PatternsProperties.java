/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "manager.patterns")
public class PatternsProperties {

    private int defaultWindowDays = 30;
    private int maxWindowDays = 90;
    private int minWindowDays = 1;

    public int getDefaultWindowDays() {
        return defaultWindowDays;
    }

    public void setDefaultWindowDays(int defaultWindowDays) {
        this.defaultWindowDays = defaultWindowDays;
    }

    public int getMaxWindowDays() {
        return maxWindowDays;
    }

    public void setMaxWindowDays(int maxWindowDays) {
        this.maxWindowDays = maxWindowDays;
    }

    public int getMinWindowDays() {
        return minWindowDays;
    }

    public void setMinWindowDays(int minWindowDays) {
        this.minWindowDays = minWindowDays;
    }

    public int clampWindowDays(int requested) {
        return Math.max(minWindowDays, Math.min(maxWindowDays, requested));
    }
}
