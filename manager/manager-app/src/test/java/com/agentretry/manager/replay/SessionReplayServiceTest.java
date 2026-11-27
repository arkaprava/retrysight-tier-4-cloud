/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.replay;

import com.agentretry.contract.SessionReplayFormat;
import com.agentretry.contract.SourceTool;
import com.agentretry.contract.TaskEventType;
import com.agentretry.contract.TaskStatus;
import com.agentretry.manager.domain.AgentEntity;
import com.agentretry.manager.domain.TaskEntity;
import com.agentretry.manager.domain.TaskEventEntity;
import com.agentretry.manager.domain.TokenUsageEntity;
import com.agentretry.manager.graphql.EventTokenUsage;
import com.agentretry.manager.graphql.TokenUsageSummary;
import com.agentretry.manager.support.TestModelPricing;
import com.agentretry.manager.service.QueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionReplayServiceTest {

    @Test
    void buildsPromptReplayWithTimelineAndInstructions() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = buildTask(taskId);
        TaskEventEntity edit = buildEvent(taskId, TaskEventType.EDIT, 1, true, "{\"changeSummary\":\"Rewrite auth filter\"}");
        TokenUsageEntity turn = buildTokenTurn(1);

        QueryService queryService = new StubQueryService(taskId, task, List.of(edit), List.of(turn));
        SessionReplayService sessionReplayService = new SessionReplayService(
                queryService,
                new ObjectMapper(),
                TestModelPricing.create()
        );

        String prompt = sessionReplayService.render(taskId, SessionReplayFormat.PROMPT);

        assertTrue(prompt.contains("structured replay"));
        assertTrue(prompt.contains("Rewrite auth filter"));
        assertTrue(prompt.contains("Fix login redirect loop"));
        assertTrue(prompt.contains("Timeline"));
    }

    private TaskEntity buildTask(UUID taskId) {
        AgentEntity agent = new AgentEntity();
        agent.setHostname("dev-mac");
        agent.setDeveloperEmail("dev@local");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setDescription("Fix login redirect loop");
        task.setSourceTool(SourceTool.CURSOR);
        task.setStatus(TaskStatus.COMPLETED);
        task.setRetryCount(1);
        task.setExternalSessionId("/tmp/session.jsonl");
        task.setStartedAt(Instant.parse("2026-07-13T10:00:00Z"));
        task.setCompletedAt(Instant.parse("2026-07-13T10:15:00Z"));
        task.setAgent(agent);
        return task;
    }

    private TaskEventEntity buildEvent(UUID taskId, TaskEventType type, int sequence, boolean retry, String payload) {
        TaskEventEntity event = new TaskEventEntity();
        event.setId(UUID.randomUUID());
        event.setEventType(type);
        event.setSequenceNum(sequence);
        event.setRetry(retry);
        event.setOccurredAt(Instant.parse("2026-07-13T10:05:00Z"));
        event.setPayload(payload);
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        event.setTask(task);
        return event;
    }

    private TokenUsageEntity buildTokenTurn(int turnIndex) {
        TokenUsageEntity turn = new TokenUsageEntity();
        turn.setTurnIndex(turnIndex);
        turn.setModel("gpt-4.1");
        turn.setInputTokens(100);
        turn.setOutputTokens(20);
        turn.setRecordedAt(Instant.parse("2026-07-13T10:05:01Z"));
        turn.setEstimatedCostUsd(BigDecimal.valueOf(0.01));
        return turn;
    }

    private static final class StubQueryService extends QueryService {
        private final UUID taskId;
        private final TaskEntity task;
        private final List<TaskEventEntity> events;
        private final List<TokenUsageEntity> tokenTurns;

        private StubQueryService(
                UUID taskId,
                TaskEntity task,
                List<TaskEventEntity> events,
                List<TokenUsageEntity> tokenTurns
        ) {
            super(
                    null,
                    null,
                    null,
                    null,
                    TestModelPricing.create(),
                    null,
                    null,
                    new com.agentretry.manager.config.AnalyticsProperties(),
                    new com.agentretry.manager.config.AgentMonitorProperties(),
                    null,
                    null
            );
            this.taskId = taskId;
            this.task = task;
            this.events = events;
            this.tokenTurns = tokenTurns;
        }

        @Override
        public TaskEntity findTask(UUID id) {
            return taskId.equals(id) ? task : null;
        }

        @Override
        public List<TaskEventEntity> findTaskEvents(UUID id) {
            return taskId.equals(id) ? events : List.of();
        }

        @Override
        public List<TokenUsageEntity> findTokenUsage(UUID id) {
            return taskId.equals(id) ? tokenTurns : List.of();
        }

        @Override
        public Map<TaskEventEntity, EventTokenUsage> resolveEventTokenUsage(List<TaskEventEntity> resolvedEvents) {
            if (resolvedEvents.isEmpty()) {
                return Map.of();
            }
            return Map.of(resolvedEvents.getFirst(), new EventTokenUsage(100, 20, 0, 0, 0, 0.01));
        }

        @Override
        public TokenUsageSummary summarizeTaskTokens(UUID id) {
            return new TokenUsageSummary(100, 20, 0, 0, 0, 0.01);
        }
    }
}
