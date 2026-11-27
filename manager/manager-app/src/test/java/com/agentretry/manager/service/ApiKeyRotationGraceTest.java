/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.manager.config.ManagerAuthProperties;
import com.agentretry.manager.config.ManagerAuthProperties;
import com.agentretry.manager.domain.ApiKeyEntity;
import com.agentretry.manager.graphql.ApiKeyCreated;
import com.agentretry.manager.repository.ApiKeyRepository;
import com.agentretry.manager.security.ApiKeyHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyRotationGraceTest {

    @Mock ApiKeyRepository apiKeyRepository;

    private ApiKeyService apiKeyService;
    private final ApiKeyHasher hasher = new ApiKeyHasher(new ManagerAuthProperties());

    @BeforeEach
    void setUp() {
        ManagerAuthProperties props = new ManagerAuthProperties();
        props.setApiKeyRotationGraceSeconds(3600);
        apiKeyService = new ApiKeyService(apiKeyRepository, hasher, props);
        when(apiKeyRepository.save(any(ApiKeyEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void rotateKeepsPreviousKeyInGraceWindow() {
        UUID id = UUID.randomUUID();
        ApiKeyEntity existing = new ApiKeyEntity();
        existing.setId(id);
        existing.setName("Agent Key");
        existing.setRole("AGENT");
        existing.setActive(true);
        existing.setKeyHash("abc");
        existing.setKeyPrefix("apk_old");
        existing.setCreatedAt(Instant.now().minusSeconds(100));
        when(apiKeyRepository.findById(id)).thenReturn(Optional.of(existing));

        ApiKeyCreated created = apiKeyService.rotateKey(id);

        assertNotNull(created.rawKey());
        assertFalse(existing.isActive());
        assertNotNull(existing.getRevokedAt());
        assertNotNull(existing.getGraceUntil());
        assertTrue(existing.getGraceUntil().isAfter(Instant.now().plusSeconds(3500)));

        ArgumentCaptor<ApiKeyEntity> captor = ArgumentCaptor.forClass(ApiKeyEntity.class);
        verify(apiKeyRepository, atLeastOnce()).save(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(ApiKeyEntity::isActive));
    }
}
