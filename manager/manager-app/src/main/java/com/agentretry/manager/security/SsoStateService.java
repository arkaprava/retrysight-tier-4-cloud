/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.security;

import com.agentretry.manager.config.ManagerAuthProperties;
import com.agentretry.manager.domain.IdentityProviderConfigEntity;
import com.agentretry.manager.repository.IdentityProviderConfigRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SsoStateService {

    private final IdentityProviderConfigRepository identityProviderConfigRepository;

    public SsoStateService(IdentityProviderConfigRepository identityProviderConfigRepository) {
        this.identityProviderConfigRepository = identityProviderConfigRepository;
    }

    public boolean isSsoEnabled() {
        return identityProviderConfigRepository.findFirstByEnabledTrue().isPresent();
    }

    public boolean hasAnyIdpConfig() {
        return identityProviderConfigRepository.count() > 0;
    }

    public boolean isDisasterRecoveryMode() {
        return !hasAnyIdpConfig();
    }

    public Optional<IdentityProviderConfigEntity> getActiveConfig() {
        return identityProviderConfigRepository.findFirstByEnabledTrue();
    }

    public Optional<IdentityProviderConfigEntity> getLatestConfig() {
        return identityProviderConfigRepository.findFirstByOrderByUpdatedAtDesc();
    }
}
