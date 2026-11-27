/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.sso.dto;

import com.agentretry.manager.domain.IdentityProviderType;

public record SsoStatusResponse(
        boolean ssoEnabled,
        boolean disasterRecovery,
        boolean localLoginAvailable,
        IdentityProviderType providerType,
        String ssoLoginUrl
) {
}
