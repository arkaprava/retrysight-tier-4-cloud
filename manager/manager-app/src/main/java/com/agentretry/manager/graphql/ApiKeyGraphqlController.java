/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import com.agentretry.manager.security.SecadminGuard;
import com.agentretry.manager.service.ApiKeyService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Controller
public class ApiKeyGraphqlController {

    private final ApiKeyService apiKeyService;

    public ApiKeyGraphqlController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @QueryMapping
    public List<ApiKeySummary> apiKeys(Authentication authentication) {
        SecadminGuard.requireSecadmin(authentication);
        return apiKeyService.listKeys();
    }

    @MutationMapping
    public ApiKeyCreated createApiKey(
            Authentication authentication,
            @Argument String name,
            @Argument String role
    ) {
        SecadminGuard.requireSecadmin(authentication);
        return apiKeyService.createKey(name, role);
    }

    @MutationMapping
    public ApiKeyCreated rotateApiKey(Authentication authentication, @Argument UUID id) {
        SecadminGuard.requireSecadmin(authentication);
        return apiKeyService.rotateKey(id);
    }

    @MutationMapping
    public boolean revokeApiKey(Authentication authentication, @Argument UUID id) {
        SecadminGuard.requireSecadmin(authentication);
        return apiKeyService.revokeKey(id);
    }
}
