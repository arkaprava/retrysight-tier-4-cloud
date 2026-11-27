/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.contract.AgentHeartbeatRequest;
import com.agentretry.manager.domain.AgentEntity;
import com.agentretry.manager.config.EvalProperties;
import com.agentretry.manager.config.ManagerAuthProperties;
import com.agentretry.manager.eval.OnlineEvalDispatcher;
import com.agentretry.manager.repository.AgentRepository;
import com.agentretry.manager.security.ApiKeyHasher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestServiceIdentityTest {

    @Mock AgentRepository agentRepository;

    private IngestService ingestService;
    private final OnlineEvalDispatcher onlineEvalDispatcher = new OnlineEvalDispatcher(null, new EvalProperties()) {
        @Override
        public void scheduleAfterTaskComplete(UUID taskId) {
            // no-op for identity tests
        }
    };

    @BeforeEach
    void setUp() {
        ManagerAuthProperties authProperties = new ManagerAuthProperties();
        ApiKeyHasher apiKeyHasher = new ApiKeyHasher(authProperties);
        ingestService = new IngestService(
                agentRepository,
                null,
                null,
                null,
                null,
                new ObjectMapper(),
                new TaskUpdatePublisher(),
                apiKeyHasher,
                null,
                null,
                new com.agentretry.manager.clickhouse.AnalyticsSinkPublisher(
                        new com.agentretry.manager.clickhouse.NoOpAnalyticsEventSink()),
                onlineEvalDispatcher,
                new com.agentretry.manager.security.PayloadRedactionService(
                        new com.agentretry.manager.config.PlatformProperties())
        );
    }

    @Test
    void resolvesByAgentIdWithoutCreatingDuplicate() {
        UUID id = UUID.randomUUID();
        AgentEntity existing = agent(id, "old-host", "dev@example.com");
        when(agentRepository.findById(id)).thenReturn(Optional.of(existing));
        when(agentRepository.save(existing)).thenReturn(existing);

        AgentHeartbeatRequest heartbeat = AgentHeartbeatRequest.withUserInfo(
                "new-host",
                "dev@example.com",
                "alice",
                "Alice",
                "alice@example.com",
                List.of(),
                0,
                Instant.now()
        );

        when(agentRepository.findByHostnameAndDeveloperEmail("new-host", "dev@example.com"))
                .thenReturn(Optional.empty());

        AgentEntity resolved = ingestService.registerOrResolveAgent("dev-agent-key", id, heartbeat);

        assertSame(existing, resolved);
        assertEquals(id, resolved.getId());
        assertEquals("new-host", existing.getHostname());
        assertEquals("alice", existing.getOsUsername());
    }

    @Test
    void fallsBackToHostnameEmailEnrollmentWhenIdUnknown() {
        UUID unknownId = UUID.randomUUID();
        when(agentRepository.findById(unknownId)).thenReturn(Optional.empty());
        when(agentRepository.findByHostnameAndDeveloperEmail("host-a", "a@example.com"))
                .thenReturn(Optional.empty());
        when(agentRepository.save(any(AgentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentHeartbeatRequest heartbeat = AgentHeartbeatRequest.withUserInfo(
                "host-a", "a@example.com", null, null, null, null, 0, Instant.now()
        );

        AgentEntity created = ingestService.registerOrResolveAgent("dev-agent-key", unknownId, heartbeat);

        assertEquals("host-a", created.getHostname());
        assertEquals("a@example.com", created.getDeveloperEmail());
        assertNotEquals(unknownId, created.getId());

        ArgumentCaptor<AgentEntity> captor = ArgumentCaptor.forClass(AgentEntity.class);
        verify(agentRepository, atLeastOnce()).save(captor.capture());
        assertEquals(created.getId(), captor.getValue().getId());
    }

    @Test
    void knownIdRejectsHeartbeatIdentityRegisteredToAnotherAgent() {
        UUID id = UUID.randomUUID();
        AgentEntity existing = agent(id, "keep-host", "keep@example.com");
        existing.setApiKeyHash(new ApiKeyHasher(new ManagerAuthProperties()).hash("dev-agent-key"));
        AgentEntity other = agent(UUID.randomUUID(), "taken-host", "other@example.com");
        when(agentRepository.findById(id)).thenReturn(Optional.of(existing));
        when(agentRepository.findByHostnameAndDeveloperEmail("taken-host", "other@example.com"))
                .thenReturn(Optional.of(other));

        AgentHeartbeatRequest heartbeat = AgentHeartbeatRequest.withUserInfo(
                "taken-host", "other@example.com", "bob", null, null, null, 0, Instant.now()
        );

        org.junit.jupiter.api.Assertions.assertThrows(
                SecurityException.class,
                () -> ingestService.registerOrResolveAgent("dev-agent-key", id, heartbeat)
        );
    }

    private static AgentEntity agent(UUID id, String hostname, String email) {
        AgentEntity entity = new AgentEntity();
        entity.setId(id);
        entity.setName(hostname);
        entity.setHostname(hostname);
        entity.setDeveloperEmail(email);
        entity.setCreatedAt(Instant.now());
        return entity;
    }
}
