/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.sso.dto;

import com.agentretry.manager.domain.IdentityProviderType;

import java.time.Instant;

public record SsoConfigResponse(
        String id,
        IdentityProviderType providerType,
        boolean enabled,
        Instant validatedAt,
        String issuerUri,
        String clientId,
        boolean clientSecretSet,
        String scopes,
        String redirectUri,
        String idpMetadataUrl,
        boolean idpMetadataXmlSet,
        String entityId,
        String acsUrl,
        String emailAttribute,
        String displayNameAttribute,
        String groupsAttribute,
        String superuserGroup,
        String createdBy,
        Instant updatedAt
) {
}
