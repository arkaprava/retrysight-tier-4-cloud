/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.contract.IngestBatchItem;
import com.agentretry.contract.IngestBatchRequest;
import com.agentretry.contract.IngestItemType;
import com.agentretry.contract.TaskCompleteRequest;
import com.agentretry.contract.TaskEventRequest;
import com.agentretry.contract.TaskEventType;
import com.agentretry.contract.TaskStatus;
import com.agentretry.manager.config.EvalProperties;
import com.agentretry.manager.config.ManagerAuthProperties;
import com.agentretry.manager.config.PlatformProperties;
import com.agentretry.manager.domain.AgentEntity;
import com.agentretry.manager.domain.TaskEntity;
import com.agentretry.manager.eval.OnlineEvalDispatcher;
import com.agentretry.manager.repository.AgentRepository;
import com.agentretry.manager.repository.TaskEventRepository;
import com.agentretry.manager.repository.TaskRepository;
import com.agentretry.manager.security.ApiKeyHasher;
import com.agentretry.manager.security.PayloadRedactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestServiceOwnershipTest {

    @Mock AgentRepository agentRepository;
    @Mock TaskRepository taskRepository;
    @Mock TaskEventRepository taskEventRepository;

    private IngestService ingestService;
    private ApiKeyHasher apiKeyHasher;

    @BeforeEach
    void setUp() {
        ManagerAuthProperties authProperties = new ManagerAuthProperties();
        apiKeyHasher = new ApiKeyHasher(authProperties);
        OnlineEvalDispatcher onlineEvalDispatcher = new OnlineEvalDispatcher(null, new EvalProperties()) {
            @Override
            public void scheduleAfterTaskComplete(UUID taskId) {
            }
        };
        ingestService = new IngestService(
                agentRepository,
                taskRepository,
                null,
                taskEventRepository,
                null,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                new TaskUpdatePublisher(),
                apiKeyHasher,
                null,
                null,
                new com.agentretry.manager.clickhouse.AnalyticsSinkPublisher(
                        new com.agentretry.manager.clickhouse.NoOpAnalyticsEventSink()),
                onlineEvalDispatcher,
                new PayloadRedactionService(new PlatformProperties())
        );
    }

    @Test
    void rejectsCrossAgentTaskEvent() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        AgentEntity owner = agent(ownerId, "host-a", "a@example.com");
        owner.setApiKeyHash(apiKeyHasher.hash("owner-key"));
        AgentEntity other = agent(otherId, "host-b", "b@example.com");
        other.setApiKeyHash(apiKeyHasher.hash("other-key"));

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setAgent(owner);

        when(agentRepository.findById(otherId)).thenReturn(Optional.of(other));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(taskEventRepository.existsByIdempotencyKey("evt-1")).thenReturn(false);

        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", taskId);
        payload.put("eventType", TaskEventType.EDIT.name());
        payload.put("occurredAt", Instant.now().toString());
        payload.put("retry", false);
        payload.put("sequenceNum", 1);

        IngestBatchRequest request = new IngestBatchRequest(
                null,
                null,
                List.of(new IngestBatchItem(
                        "evt-1",
                        IngestItemType.TASK_EVENT,
                        payload
                ))
        );

        var response = ingestService.processBatch(otherId, request);

        assertEquals(0, response.accepted(), () -> "errors=" + response.errors());
        assertEquals(1, response.errors().size(), () -> "errors=" + response.errors());
        assertTrue(
                response.errors().getFirst().contains("does not belong to agent"),
                () -> response.errors().getFirst()
        );
        verify(taskEventRepository, never()).save(any());
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
