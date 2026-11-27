/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.security;

import org.springframework.security.core.Authentication;

/**
 * Backward-compatible SECADMIN checks. New code should prefer {@link RoleGuard}.
 */
public final class SecadminGuard {

    private SecadminGuard() {
    }

    public static String requireSecadmin(Authentication authentication) {
        return RoleGuard.requireSecadmin(authentication);
    }

    public static boolean hasSecadminRole(Authentication authentication) {
        return RoleGuard.hasSecadminRole(authentication);
    }
}
