/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.contract.TaskEventType;
import com.agentretry.manager.domain.TaskEventEntity;
import com.agentretry.manager.domain.TokenUsageEntity;
import com.agentretry.manager.support.TestModelPricing;
import com.agentretry.manager.graphql.EventTokenUsage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventTokenUsageMatcherTest {

    private final ModelPricingService pricingService = TestModelPricing.create();

    @Test
    void matchesTokenTurnByPayloadTurnIndex() {
        UUID taskId = UUID.randomUUID();
        TaskEventEntity event = event(taskId, TaskEventType.TOKEN_USAGE, "{\"turn\":2}");
        event.setOccurredAt(Instant.parse("2026-01-01T12:00:05Z"));

        List<TokenUsageEntity> turns = List.of(
                token(taskId, 1, 100, 10, Instant.parse("2026-01-01T12:00:01Z")),
                token(taskId, 2, 500, 40, Instant.parse("2026-01-01T12:00:02Z"))
        );

        EventTokenUsage usage = EventTokenUsageMatcher.match(event, turns, pricingService);

        assertNotNull(usage);
        assertEquals(500, usage.inputTokens());
        assertEquals(40, usage.outputTokens());
        assertEquals("claude-sonnet-4", usage.model());
    }

    @Test
    void matchesNearestTokenTurnByTimeWhenTurnMissing() {
        UUID taskId = UUID.randomUUID();
        TaskEventEntity event = event(taskId, TaskEventType.EDIT, "{\"filePath\":\"src/App.tsx\"}");
        event.setOccurredAt(Instant.parse("2026-01-01T12:00:03Z"));

        List<TokenUsageEntity> turns = List.of(
                token(taskId, 1, 100, 10, Instant.parse("2026-01-01T12:00:01Z")),
                token(taskId, 2, 900, 80, Instant.parse("2026-01-01T12:00:02Z"))
        );

        EventTokenUsage usage = EventTokenUsageMatcher.match(event, turns, pricingService);

        assertNotNull(usage);
        assertEquals(900, usage.inputTokens());
        assertEquals(80, usage.outputTokens());
    }

    @Test
    void returnsNullWhenNoTokenDataAvailable() {
        UUID taskId = UUID.randomUUID();
        TaskEventEntity event = event(taskId, TaskEventType.SESSION_START, null);
        event.setOccurredAt(Instant.parse("2026-01-01T12:00:00Z"));

        EventTokenUsage usage = EventTokenUsageMatcher.match(event, List.of(), pricingService);

        assertNull(usage);
    }

    @Test
    void attributeUsageUsesSameDedupeKeyForSameTurn() {
        UUID taskId = UUID.randomUUID();
        Instant turnTime = Instant.parse("2026-01-01T12:00:02Z");
        List<TokenUsageEntity> turns = List.of(token(taskId, 2, 900, 80, turnTime));

        TaskEventEntity edit = event(taskId, TaskEventType.EDIT, "{\"turn\":2}");
        edit.setRetry(true);
        edit.setOccurredAt(Instant.parse("2026-01-01T12:00:03Z"));

        TaskEventEntity testFail = event(taskId, TaskEventType.TEST_FAIL, "{\"turn\":2}");
        testFail.setRetry(true);
        testFail.setOccurredAt(Instant.parse("2026-01-01T12:00:04Z"));

        var editUsage = EventTokenUsageMatcher.attributeUsage(edit, turns, pricingService);
        var testFailUsage = EventTokenUsageMatcher.attributeUsage(testFail, turns, pricingService);

        assertTrue(editUsage.isPresent());
        assertTrue(testFailUsage.isPresent());
        assertEquals(editUsage.get().dedupeKey(), testFailUsage.get().dedupeKey());
    }

    private static TaskEventEntity event(UUID taskId, TaskEventType type, String payload) {
        TaskEventEntity event = new TaskEventEntity();
        event.setId(UUID.randomUUID());
        event.setEventType(type);
        event.setPayload(payload);
        com.agentretry.manager.domain.TaskEntity task = new com.agentretry.manager.domain.TaskEntity();
        task.setId(taskId);
        event.setTask(task);
        return event;
    }

    private static TokenUsageEntity token(
            UUID taskId,
            int turnIndex,
            long input,
            long output,
            Instant recordedAt
    ) {
        TokenUsageEntity usage = new TokenUsageEntity();
        com.agentretry.manager.domain.TaskEntity task = new com.agentretry.manager.domain.TaskEntity();
        task.setId(taskId);
        usage.setTask(task);
        usage.setTurnIndex(turnIndex);
        usage.setModel("claude-sonnet-4");
        usage.setInputTokens(input);
        usage.setOutputTokens(output);
        usage.setRecordedAt(recordedAt);
        return usage;
    }
}
