/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.sso;

import com.agentretry.manager.config.ManagerAuthProperties;
import com.agentretry.manager.domain.IdentityProviderConfigEntity;
import com.agentretry.manager.domain.IdentityProviderType;
import com.agentretry.manager.security.SsoStateService;
import com.agentretry.manager.sso.dto.SsoStatusResponse;
import org.springframework.stereotype.Service;

@Service
public class SsoStatusService {

    private final SsoStateService ssoStateService;
    private final SsoConfigService ssoConfigService;
    private final ManagerAuthProperties authProperties;

    public SsoStatusService(
            SsoStateService ssoStateService,
            SsoConfigService ssoConfigService,
            ManagerAuthProperties authProperties
    ) {
        this.ssoStateService = ssoStateService;
        this.ssoConfigService = ssoConfigService;
        this.authProperties = authProperties;
    }

    public SsoStatusResponse getStatus() {
        boolean ssoEnabled = ssoStateService.isSsoEnabled();
        boolean disasterRecovery = ssoStateService.isDisasterRecoveryMode();
        boolean localLoginAvailable = !ssoEnabled || disasterRecovery;
        IdentityProviderType providerType = ssoStateService.getActiveConfig()
                .map(IdentityProviderConfigEntity::getProviderType)
                .orElse(null);
        String ssoLoginUrl = providerType == null ? null : ssoConfigService.buildSsoLoginUrl(providerType);
        return new SsoStatusResponse(ssoEnabled, disasterRecovery, localLoginAvailable, providerType, ssoLoginUrl);
    }
}
