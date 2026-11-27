/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.manager.domain.UiUserEntity;
import com.agentretry.manager.domain.UiUserRole;
import com.agentretry.manager.graphql.AuthPayload;
import com.agentretry.manager.graphql.McpTokenPayload;
import com.agentretry.manager.repository.UiUserRepository;
import com.agentretry.manager.security.JwtService;
import com.agentretry.manager.security.LocalLoginPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    public static final UUID CONFIG_SECADMIN_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final UiUserRepository uiUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LocalLoginPolicy localLoginPolicy;

    public AuthService(
            UiUserRepository uiUserRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LocalLoginPolicy localLoginPolicy
    ) {
        this.uiUserRepository = uiUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.localLoginPolicy = localLoginPolicy;
    }

    public AuthPayload login(String email, String password) {
        if (!localLoginPolicy.isLocalLoginPermitted(email)) {
            throw new IllegalArgumentException("Local login is disabled");
        }

        if (localLoginPolicy.isConfigSecadminLogin(email, password)) {
            String token = jwtService.generateToken(
                    CONFIG_SECADMIN_ID,
                    email,
                    List.of(UiUserRole.SECADMIN.name())
            );
            return new AuthPayload(token, email, "Security Admin", List.of(UiUserRole.SECADMIN.name()));
        }

        UiUserEntity user = uiUserRepository.findByEmailAndActiveTrue(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        String token = jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                List.of(user.getRole().name())
        );
        return new AuthPayload(
                token,
                user.getEmail(),
                user.getDisplayName(),
                List.of(user.getRole().name())
        );
    }

    public AuthPayload issueSsoToken(UiUserEntity user) {
        String token = jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                List.of(user.getRole().name())
        );
        return new AuthPayload(
                token,
                user.getEmail(),
                user.getDisplayName(),
                List.of(user.getRole().name())
        );
    }

    public McpTokenPayload issueMcpToken(String email, String password) {
        AuthPayload login = login(email, password);
        long expirationSeconds = localLoginPolicy.mcpTokenExpirationSeconds();
        UUID userId = resolveUserId(email);
        String token = jwtService.generateMcpToken(
                userId,
                login.email(),
                login.roles(),
                expirationSeconds
        );
        return new McpTokenPayload(token, login.email(), jwtService.tokenExpiration(expirationSeconds));
    }

    private UUID resolveUserId(String email) {
        if (localLoginPolicy.isConfigSecadminEmail(email)) {
            return CONFIG_SECADMIN_ID;
        }
        return uiUserRepository.findByEmailAndActiveTrue(email)
                .map(UiUserEntity::getId)
                .orElse(CONFIG_SECADMIN_ID);
    }
}
