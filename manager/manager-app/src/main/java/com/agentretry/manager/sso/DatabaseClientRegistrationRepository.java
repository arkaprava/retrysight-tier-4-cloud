/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.sso;

import com.agentretry.manager.domain.IdentityProviderConfigEntity;
import com.agentretry.manager.domain.IdentityProviderType;
import com.agentretry.manager.security.SecretEncryptionService;
import com.agentretry.manager.security.SsoStateService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class DatabaseClientRegistrationRepository implements ClientRegistrationRepository {

    private final SsoStateService ssoStateService;
    private final SecretEncryptionService secretEncryptionService;
    private volatile ClientRegistration cachedRegistration;

    public DatabaseClientRegistrationRepository(
            SsoStateService ssoStateService,
            SecretEncryptionService secretEncryptionService
    ) {
        this.ssoStateService = ssoStateService;
        this.secretEncryptionService = secretEncryptionService;
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        if (!SsoConfigService.REGISTRATION_ID.equals(registrationId)) {
            return null;
        }
        ClientRegistration registration = cachedRegistration;
        if (registration != null) {
            return registration;
        }
        return refresh();
    }

    public synchronized ClientRegistration refresh() {
        cachedRegistration = loadFromDatabase();
        return cachedRegistration;
    }

    private ClientRegistration loadFromDatabase() {
        return ssoStateService.getActiveConfig()
                .filter(config -> config.getProviderType() == IdentityProviderType.OIDC)
                .map(this::buildRegistration)
                .orElse(null);
    }

    private ClientRegistration buildRegistration(IdentityProviderConfigEntity config) {
        String clientSecret = secretEncryptionService.decrypt(config.getClientSecretEnc());
        return ClientRegistration.withRegistrationId(SsoConfigService.REGISTRATION_ID)
                .clientId(config.getClientId())
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(config.getRedirectUri())
                .scope(Arrays.asList(config.getScopes().split("\\s+")))
                .issuerUri(config.getIssuerUri())
                .build();
    }
}
