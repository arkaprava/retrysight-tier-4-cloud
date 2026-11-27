/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.security;

import com.agentretry.manager.config.ManagerAuthProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LocalLoginPolicy {

    private final ManagerAuthProperties authProperties;
    private final SsoStateService ssoStateService;
    private final PasswordEncoder passwordEncoder;
    private String secadminPasswordHash;

    public LocalLoginPolicy(
            ManagerAuthProperties authProperties,
            SsoStateService ssoStateService,
            PasswordEncoder passwordEncoder
    ) {
        this.authProperties = authProperties;
        this.ssoStateService = ssoStateService;
        this.passwordEncoder = passwordEncoder;
        encodeSecadminPassword();
    }

    @PostConstruct
    void postConstructEncodeSecadminPassword() {
        encodeSecadminPassword();
    }

    void encodeSecadminPassword() {
        String password = authProperties.getSecadmin().getPassword();
        if (password != null && !password.isBlank()) {
            secadminPasswordHash = passwordEncoder.encode(password);
        }
    }

    public boolean isConfigSecadminEmail(String email) {
        return authProperties.getSecadmin().getEmail().equalsIgnoreCase(email);
    }

    public boolean isConfigSecadminLogin(String email, String password) {
        ManagerAuthProperties.Secadmin secadmin = authProperties.getSecadmin();
        if (!secadmin.getEmail().equalsIgnoreCase(email)) {
            return false;
        }
        if (password == null || secadminPasswordHash == null) {
            return false;
        }
        return passwordEncoder.matches(password, secadminPasswordHash);
    }

    public boolean isLocalLoginPermitted(String email) {
        if (isConfigSecadminEmail(email)) {
            // Config-file secadmin is break-glass access — always permitted.
            return true;
        }
        return false;
    }

    public long mcpTokenExpirationSeconds() {
        return authProperties.getMcpTokenExpirationDays() * 24 * 3600;
    }
}
