/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.contract.SourceTool;
import com.agentretry.contract.TaskEventType;
import com.agentretry.contract.TaskStatus;
import com.agentretry.manager.domain.AgentEntity;
import com.agentretry.manager.domain.TaskEntity;
import com.agentretry.manager.domain.TaskEventEntity;
import com.agentretry.manager.domain.TokenUsageEntity;
import com.agentretry.manager.config.AnalyticsProperties;
import com.agentretry.manager.graphql.RetryAnalysisSummary;
import com.agentretry.manager.repository.TaskEventRepository;
import com.agentretry.manager.repository.TaskRepository;
import com.agentretry.manager.repository.TokenUsageRepository;
import com.agentretry.manager.support.TestModelPricing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetryAnalyticsServiceTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private TaskEventRepository taskEventRepository;
    @Mock
    private TokenUsageRepository tokenUsageRepository;

    private RetryAnalyticsService retryAnalyticsService;
    private final ModelPricingService pricingService = TestModelPricing.create();

    @BeforeEach
    void setUp() {
        CostAnalyticsService costAnalyticsService = new CostAnalyticsService(
                null,
                taskRepository,
                taskEventRepository,
                tokenUsageRepository,
                pricingService,
                null,
                new AnalyticsProperties(),
                null
        );
        retryAnalyticsService = new RetryAnalyticsService(
                taskRepository,
                taskEventRepository,
                tokenUsageRepository,
                pricingService,
                costAnalyticsService
        );
    }

    @Test
    void retryAnalysisGroupsRetriesAndCostByNormalizedModel() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = task(taskId);

        TokenUsageEntity sonnetTurn = token(task, 1, "claude-sonnet-4", 1000, 100, "0.012300");
        TokenUsageEntity opusTurn = token(task, 2, "claude-opus-4", 500, 50, "0.050000");

        TaskEventEntity sonnetRetry = retryEvent(task, TaskEventType.EDIT, "{\"turn\":1}", 1);
        TaskEventEntity opusRetry = retryEvent(task, TaskEventType.DIFF_REJECTED, "{\"turn\":2}", 2);

        when(taskRepository.findAll(any(Specification.class))).thenReturn(List.of(task));
        when(tokenUsageRepository.findByTaskIdOrderByTurnIndexAsc(taskId)).thenReturn(List.of(sonnetTurn, opusTurn));
        when(taskEventRepository.findByTaskIdInAndRetryTrueOrderBySequenceNumAsc(List.of(taskId)))
                .thenReturn(List.of(sonnetRetry, opusRetry));

        RetryAnalysisSummary summary = retryAnalyticsService.retryAnalysis(null, null, null, null);

        assertEquals(2, summary.retriesByModel().stream().mapToDouble(metric -> metric.value()).sum(), 0.001);
        assertTrue(summary.retriesByModel().stream().anyMatch(metric ->
                "claude-sonnet".equals(metric.model()) && metric.value() == 1));
        assertTrue(summary.retriesByModel().stream().anyMatch(metric ->
                "claude-opus".equals(metric.model()) && metric.value() == 1));

        double retryCostTotal = summary.retryCostByModel().stream().mapToDouble(metric -> metric.value()).sum();
        assertEquals(0.0623, retryCostTotal, 0.0001);

        assertEquals(0, summary.unattributedRetryCount());
        assertEquals(2, summary.topRetryTasksByModel().size());
    }

    @Test
    void retryAnalysisCountsUnattributedRetriesWithoutTokenMatch() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = task(taskId);
        TaskEventEntity orphanRetry = retryEvent(task, TaskEventType.EDIT, "{}", 1);
        orphanRetry.setOccurredAt(Instant.parse("2026-01-01T13:00:00Z"));

        when(taskRepository.findAll(any(Specification.class))).thenReturn(List.of(task));
        when(tokenUsageRepository.findByTaskIdOrderByTurnIndexAsc(taskId)).thenReturn(List.of());
        when(taskEventRepository.findByTaskIdInAndRetryTrueOrderBySequenceNumAsc(List.of(taskId)))
                .thenReturn(List.of(orphanRetry));

        RetryAnalysisSummary summary = retryAnalyticsService.retryAnalysis(null, null, null, null);

        assertEquals(1, summary.unattributedRetryCount());
        assertTrue(summary.retriesByModel().stream().anyMatch(metric ->
                "unknown".equals(metric.model()) && metric.value() == 1));
    }

    private static TaskEntity task(UUID taskId) {
        AgentEntity agent = new AgentEntity();
        agent.setId(UUID.randomUUID());
        agent.setHostname("dev-1");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setAgent(agent);
        task.setDescription("Fix login");
        task.setSourceTool(SourceTool.CURSOR);
        task.setStatus(TaskStatus.COMPLETED);
        task.setRetryCount(2);
        task.setStartedAt(Instant.parse("2026-01-01T12:00:00Z"));
        return task;
    }

    private static TokenUsageEntity token(
            TaskEntity task,
            int turnIndex,
            String model,
            long input,
            long output,
            String cost
    ) {
        TokenUsageEntity usage = new TokenUsageEntity();
        usage.setId(UUID.randomUUID());
        usage.setTask(task);
        usage.setTurnIndex(turnIndex);
        usage.setModel(model);
        usage.setInputTokens(input);
        usage.setOutputTokens(output);
        usage.setEstimatedCostUsd(new BigDecimal(cost));
        usage.setRecordedAt(Instant.parse("2026-01-01T12:00:0" + turnIndex + "Z"));
        return usage;
    }

    private static TaskEventEntity retryEvent(TaskEntity task, TaskEventType type, String payload, int sequence) {
        TaskEventEntity event = new TaskEventEntity();
        event.setId(UUID.randomUUID());
        event.setTask(task);
        event.setEventType(type);
        event.setPayload(payload);
        event.setRetry(true);
        event.setSequenceNum(sequence);
        event.setOccurredAt(Instant.parse("2026-01-01T12:00:0" + sequence + "Z"));
        return event;
    }
}
