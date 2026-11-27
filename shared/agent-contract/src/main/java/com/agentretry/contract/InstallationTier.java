/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.contract;

/**
 * Installation tiers control which product capabilities are available.
 * Higher tiers include all lower-tier capabilities when enabled.
 */
public enum InstallationTier {
    TIER_1_CORE(1),
    TIER_2_ANALYTICS(2),
    TIER_3_INTEGRATION(3),
    TIER_4_CLOUD(4);

    private final int level;

    InstallationTier(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    public boolean includes(InstallationTier required) {
        return this.level >= required.level;
    }

    public static InstallationTier from(String value) {
        if (value == null || value.isBlank()) {
            return TIER_1_CORE;
        }
        return InstallationTier.valueOf(value.trim().toUpperCase());
    }
}
