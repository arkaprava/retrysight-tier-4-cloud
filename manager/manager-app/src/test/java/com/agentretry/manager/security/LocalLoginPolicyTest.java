/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.security;

import com.agentretry.manager.config.ManagerAuthProperties;
import com.agentretry.manager.domain.IdentityProviderConfigEntity;
import com.agentretry.manager.domain.IdentityProviderType;
import com.agentretry.manager.repository.IdentityProviderConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalLoginPolicyTest {

    private ManagerAuthProperties authProperties;
    private IdentityProviderConfigRepository repository;
    private LocalLoginPolicy policy;

    @BeforeEach
    void setUp() {
        authProperties = new ManagerAuthProperties();
        authProperties.getSecadmin().setEmail("secadmin@local");
        authProperties.getSecadmin().setPassword("secret");
        repository = mock(IdentityProviderConfigRepository.class);
        policy = new LocalLoginPolicy(authProperties, new SsoStateService(repository), new BCryptPasswordEncoder());
    }

    @Test
    void secadminCanLoginDuringBootstrap() {
        when(repository.findFirstByEnabledTrue()).thenReturn(Optional.empty());
        when(repository.count()).thenReturn(0L);

        assertTrue(policy.isLocalLoginPermitted("secadmin@local"));
        assertTrue(policy.isConfigSecadminLogin("secadmin@local", "secret"));
        assertFalse(policy.isLocalLoginPermitted("user@company.com"));
    }

    @Test
    void secadminCanLoginWhileConfiguringSso() {
        IdentityProviderConfigEntity config = new IdentityProviderConfigEntity();
        config.setId(UUID.randomUUID());
        config.setProviderType(IdentityProviderType.OIDC);
        config.setEnabled(false);
        when(repository.findFirstByEnabledTrue()).thenReturn(Optional.empty());
        when(repository.count()).thenReturn(1L);

        assertTrue(policy.isLocalLoginPermitted("secadmin@local"));
        assertFalse(policy.isLocalLoginPermitted("user@company.com"));
    }

    @Test
    void localLoginDisabledWhenSsoEnabledExceptConfigSecadmin() {
        IdentityProviderConfigEntity config = new IdentityProviderConfigEntity();
        config.setId(UUID.randomUUID());
        config.setProviderType(IdentityProviderType.OIDC);
        config.setEnabled(true);
        when(repository.findFirstByEnabledTrue()).thenReturn(Optional.of(config));
        when(repository.count()).thenReturn(1L);

        assertTrue(policy.isLocalLoginPermitted("secadmin@local"));
        assertFalse(policy.isLocalLoginPermitted("user@company.com"));
    }

    @Test
    void secadminCanLoginInDisasterRecoveryMode() {
        when(repository.findFirstByEnabledTrue()).thenReturn(Optional.empty());
        when(repository.count()).thenReturn(0L);

        assertTrue(policy.isLocalLoginPermitted("secadmin@local"));
    }
}
