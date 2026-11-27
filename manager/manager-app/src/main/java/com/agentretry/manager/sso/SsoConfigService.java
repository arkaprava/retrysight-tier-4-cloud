/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.sso;

import com.agentretry.manager.config.ManagerAuthProperties;
import com.agentretry.manager.domain.IdentityProviderConfigEntity;
import com.agentretry.manager.domain.IdentityProviderType;
import com.agentretry.manager.repository.IdentityProviderConfigRepository;
import com.agentretry.manager.security.SecretEncryptionService;
import com.agentretry.manager.sso.dto.SsoConfigRequest;
import com.agentretry.manager.sso.dto.SsoConfigResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SsoConfigService {

    public static final String REGISTRATION_ID = "manager";

    private final IdentityProviderConfigRepository repository;
    private final SecretEncryptionService secretEncryptionService;
    private final ManagerAuthProperties authProperties;
    private final DatabaseClientRegistrationRepository clientRegistrationRepository;
    private final DatabaseRelyingPartyRegistrationRepository relyingPartyRegistrationRepository;

    public SsoConfigService(
            IdentityProviderConfigRepository repository,
            SecretEncryptionService secretEncryptionService,
            ManagerAuthProperties authProperties,
            DatabaseClientRegistrationRepository clientRegistrationRepository,
            DatabaseRelyingPartyRegistrationRepository relyingPartyRegistrationRepository
    ) {
        this.repository = repository;
        this.secretEncryptionService = secretEncryptionService;
        this.authProperties = authProperties;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.relyingPartyRegistrationRepository = relyingPartyRegistrationRepository;
    }

    public SsoConfigResponse getConfig() {
        return repository.findFirstByOrderByUpdatedAtDesc()
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional
    public SsoConfigResponse saveConfig(SsoConfigRequest request, String updatedBy) {
        validateRequest(request);
        IdentityProviderConfigEntity entity = repository.findFirstByOrderByUpdatedAtDesc()
                .orElseGet(IdentityProviderConfigEntity::new);
        Instant now = Instant.now();
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setCreatedAt(now);
            entity.setCreatedBy(updatedBy);
            entity.setEnabled(false);
            entity.setValidatedAt(null);
        }

        entity.setProviderType(request.providerType());
        entity.setIssuerUri(trim(request.issuerUri()));
        entity.setClientId(trim(request.clientId()));
        if (request.clientSecret() != null && !request.clientSecret().isBlank()) {
            entity.setClientSecretEnc(secretEncryptionService.encrypt(request.clientSecret()));
        }
        entity.setScopes(defaultScopes(request.scopes()));
        entity.setRedirectUri(buildOidcRedirectUri());
        entity.setIdpMetadataUrl(trim(request.idpMetadataUrl()));
        if (request.idpMetadataXml() != null && !request.idpMetadataXml().isBlank()) {
            entity.setIdpMetadataXml(request.idpMetadataXml().trim());
        }
        entity.setEntityId(buildSpEntityId());
        entity.setAcsUrl(buildAcsUrl());
        entity.setEmailAttribute(defaultAttribute(request.emailAttribute(), "email"));
        entity.setDisplayNameAttribute(defaultAttribute(request.displayNameAttribute(), "name"));
        entity.setGroupsAttribute(defaultAttribute(request.groupsAttribute(), "groups"));
        entity.setSuperuserGroup(trim(request.superuserGroup()));
        entity.setUpdatedAt(now);
        entity.setValidatedAt(null);

        repository.deleteAll();
        repository.save(entity);
        refreshRegistrations();
        return toResponse(entity);
    }

    @Transactional
    public void markValidated() {
        IdentityProviderConfigEntity entity = requireConfig();
        entity.setValidatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
    }

    @Transactional
    public SsoConfigResponse enableSso() {
        IdentityProviderConfigEntity entity = requireConfig();
        if (entity.getValidatedAt() == null) {
            throw new IllegalStateException("SSO must be tested before enabling");
        }
        if (entity.getSuperuserGroup() == null || entity.getSuperuserGroup().isBlank()) {
            throw new IllegalStateException("Superuser group must be configured before enabling SSO");
        }
        entity.setEnabled(true);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
        refreshRegistrations();
        return toResponse(entity);
    }

    @Transactional
    public void deleteConfig() {
        repository.deleteAll();
        refreshRegistrations();
    }

    public IdentityProviderConfigEntity requireConfig() {
        return repository.findFirstByOrderByUpdatedAtDesc()
                .orElseThrow(() -> new IllegalStateException("No SSO configuration found"));
    }

    public String decryptClientSecret(IdentityProviderConfigEntity config) {
        return secretEncryptionService.decrypt(config.getClientSecretEnc());
    }

    public void refreshRegistrations() {
        clientRegistrationRepository.refresh();
        relyingPartyRegistrationRepository.refresh();
    }

    public String buildOidcRedirectUri() {
        return authProperties.getSso().getManagerBaseUrl()
                + "/login/oauth2/code/" + REGISTRATION_ID;
    }

    public String buildAcsUrl() {
        return authProperties.getSso().getManagerBaseUrl()
                + "/login/saml2/sso/" + REGISTRATION_ID;
    }

    public String buildSpEntityId() {
        return authProperties.getSso().getManagerBaseUrl() + "/saml2/service-provider-metadata/" + REGISTRATION_ID;
    }

    public String buildSsoLoginUrl(IdentityProviderType type) {
        if (type == IdentityProviderType.OIDC) {
            return authProperties.getSso().getManagerBaseUrl()
                    + "/oauth2/authorization/" + REGISTRATION_ID;
        }
        return authProperties.getSso().getManagerBaseUrl()
                + "/saml2/authenticate/" + REGISTRATION_ID;
    }

    private void validateRequest(SsoConfigRequest request) {
        if (request.providerType() == IdentityProviderType.OIDC) {
            if (isBlank(request.issuerUri()) || isBlank(request.clientId())) {
                throw new IllegalArgumentException("OIDC issuer URI and client ID are required");
            }
        } else if (request.providerType() == IdentityProviderType.SAML) {
            if (isBlank(request.idpMetadataUrl()) && isBlank(request.idpMetadataXml())) {
                throw new IllegalArgumentException("SAML metadata URL or XML is required");
            }
        }
    }

    private SsoConfigResponse toResponse(IdentityProviderConfigEntity entity) {
        return new SsoConfigResponse(
                entity.getId().toString(),
                entity.getProviderType(),
                entity.isEnabled(),
                entity.getValidatedAt(),
                entity.getIssuerUri(),
                entity.getClientId(),
                entity.getClientSecretEnc() != null && !entity.getClientSecretEnc().isBlank(),
                entity.getScopes(),
                entity.getRedirectUri(),
                entity.getIdpMetadataUrl(),
                entity.getIdpMetadataXml() != null && !entity.getIdpMetadataXml().isBlank(),
                entity.getEntityId(),
                entity.getAcsUrl(),
                entity.getEmailAttribute(),
                entity.getDisplayNameAttribute(),
                entity.getGroupsAttribute(),
                entity.getSuperuserGroup(),
                entity.getCreatedBy(),
                entity.getUpdatedAt()
        );
    }

    private static String defaultScopes(String scopes) {
        return isBlank(scopes) ? "openid profile email" : scopes.trim();
    }

    private static String defaultAttribute(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
