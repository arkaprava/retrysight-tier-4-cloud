/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.sso.dto;

import com.agentretry.manager.domain.IdentityProviderType;

public record SsoConfigRequest(
        IdentityProviderType providerType,
        String issuerUri,
        String clientId,
        String clientSecret,
        String scopes,
        String idpMetadataUrl,
        String idpMetadataXml,
        String emailAttribute,
        String displayNameAttribute,
        String groupsAttribute,
        String superuserGroup
) {
}
