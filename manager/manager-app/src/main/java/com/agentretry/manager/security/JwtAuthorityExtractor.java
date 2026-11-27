/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.security;

import com.agentretry.manager.domain.UiUserRole;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class JwtAuthorityExtractor {

    private JwtAuthorityExtractor() {
    }

    public static Collection<? extends GrantedAuthority> fromClaims(Claims claims) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        Object rolesClaim = claims.get("roles");
        if (rolesClaim instanceof List<?> roles) {
            for (Object role : roles) {
                if (role != null) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toString()));
                }
            }
        }
        if (authorities.isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + UiUserRole.UI.name()));
        }
        return authorities;
    }

    public static List<String> roleNames(Claims claims) {
        Object rolesClaim = claims.get("roles");
        if (rolesClaim instanceof List<?> roles) {
            return roles.stream().map(Object::toString).toList();
        }
        return List.of(UiUserRole.UI.name());
    }
}
