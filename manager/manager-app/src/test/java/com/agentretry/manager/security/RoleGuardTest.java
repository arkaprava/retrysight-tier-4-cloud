/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleGuardTest {

    @Test
    void recognizesAdminAndSecadminRoles() {
        Authentication admin = auth("admin@local", "ROLE_ADMIN");
        Authentication secadmin = auth("sec@local", "ROLE_SECADMIN");
        Authentication ui = auth("user@local", "ROLE_UI");

        assertTrue(RoleGuard.hasAdminRole(admin));
        assertFalse(RoleGuard.hasSecadminRole(admin));
        assertTrue(RoleGuard.hasSecadminRole(secadmin));
        assertFalse(RoleGuard.hasAdminRole(ui));
        assertEquals("admin@local", RoleGuard.requireAdminOrSecadmin(admin));
        assertEquals("sec@local", RoleGuard.requireAdminOrSecadmin(secadmin));
    }

    @Test
    void rejectsUiFromAdminOrSecadmin() {
        Authentication ui = auth("user@local", "ROLE_UI");
        assertThrows(ResponseStatusException.class, () -> RoleGuard.requireAdminOrSecadmin(ui));
        assertThrows(ResponseStatusException.class, () -> RoleGuard.requireAdmin(ui));
        assertThrows(ResponseStatusException.class, () -> RoleGuard.requireSecadmin(ui));
    }

    private static Authentication auth(String name, String authority) {
        return new UsernamePasswordAuthenticationToken(
                name,
                "n/a",
                List.of(new SimpleGrantedAuthority(authority))
        );
    }
}
