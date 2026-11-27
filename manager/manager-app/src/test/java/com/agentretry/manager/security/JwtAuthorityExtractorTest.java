/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthorityExtractorTest {

    @Test
    void mapsRolesClaimToGrantedAuthorities() {
        Claims claims = new DefaultClaims(Map.of("roles", List.of("UI", "SECADMIN")));

        Collection<? extends GrantedAuthority> authorities = JwtAuthorityExtractor.fromClaims(claims);

        assertEquals(2, authorities.size());
        assertTrue(authorities.stream().anyMatch(auth -> "ROLE_SECADMIN".equals(auth.getAuthority())));
    }

    @Test
    void mapsAdminRoleClaim() {
        Claims claims = new DefaultClaims(Map.of("roles", List.of("ADMIN")));

        Collection<? extends GrantedAuthority> authorities = JwtAuthorityExtractor.fromClaims(claims);

        assertEquals(1, authorities.size());
        assertTrue(authorities.stream().anyMatch(auth -> "ROLE_ADMIN".equals(auth.getAuthority())));
    }

    @Test
    void defaultsToUiRoleWhenClaimMissing() {
        Claims claims = new DefaultClaims(Map.of());

        Collection<? extends GrantedAuthority> authorities = JwtAuthorityExtractor.fromClaims(claims);

        assertEquals(1, authorities.size());
        assertEquals("ROLE_UI", authorities.iterator().next().getAuthority());
    }
}
