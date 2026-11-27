/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

/**
 * Role checks for UI users. Prefer this over ad-hoc authority scans for new admin flows.
 *
 * <p>{@code SECADMIN} is the platform superuser and may access every UI surface.
 * Use {@link #requireAdminOrSecadmin} for pages/APIs that ADMIN may use; keep
 * {@link #requireSecadmin} for Security/SSO and other superuser-only controls.
 * Use {@link #requireAdmin} only when an ADMIN-specific approval signature is required
 * (SECADMIN alone must not satisfy that leg).
 */
public final class RoleGuard {

    private RoleGuard() {
    }

    public static String requireSecadmin(Authentication authentication) {
        if (authentication == null || !hasSecadminRole(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SECADMIN role required");
        }
        return authentication.getName();
    }

    public static String requireAdmin(Authentication authentication) {
        if (authentication == null || !hasAdminRole(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN role required");
        }
        return authentication.getName();
    }

    public static String requireAdminOrSecadmin(Authentication authentication) {
        if (authentication == null || (!hasAdminRole(authentication) && !hasSecadminRole(authentication))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN or SECADMIN role required");
        }
        return authentication.getName();
    }

    public static String requireAuthenticated(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return authentication.getName();
    }

    public static boolean hasSecadminRole(Authentication authentication) {
        return hasRole(authentication, "ROLE_SECADMIN");
    }

    public static boolean hasAdminRole(Authentication authentication) {
        return hasRole(authentication, "ROLE_ADMIN");
    }

    public static boolean hasUiRole(Authentication authentication) {
        return hasRole(authentication, "ROLE_UI");
    }

    private static boolean hasRole(Authentication authentication, String authority) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }
}
