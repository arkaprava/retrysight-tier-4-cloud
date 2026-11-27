/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.contract.InstallationTier;
import com.agentretry.contract.SourceTool;
import com.agentretry.contract.TaskEventType;
import com.agentretry.contract.TaskStatus;
import com.agentretry.manager.domain.AgentEntity;
import com.agentretry.manager.domain.TaskEntity;
import com.agentretry.manager.domain.TaskEventEntity;
import com.agentretry.manager.domain.TokenUsageEntity;
import com.agentretry.manager.graphql.DashboardSummary;
import com.agentretry.manager.graphql.LlmRunMetric;
import com.agentretry.manager.config.PlatformProperties;
import com.agentretry.manager.config.AnalyticsProperties;
import com.agentretry.manager.config.EvalProperties;
import com.agentretry.manager.eval.OnlineEvalDispatcher;
import com.agentretry.manager.platform.FeatureGateService;
import com.agentretry.manager.repository.AgentRepository;
import com.agentretry.manager.support.TestModelPricing;
import com.agentretry.manager.repository.TaskEventRepository;
import com.agentretry.manager.repository.TaskRepository;
import com.agentretry.manager.repository.TokenUsageRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryServiceRetryTokenCostTest {

    @Mock
    private AgentRepository agentRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private TaskEventRepository taskEventRepository;
    @Mock
    private TokenUsageRepository tokenUsageRepository;

    private QueryService queryService;
    private final ModelPricingService pricingService = TestModelPricing.create();

    @BeforeEach
    void setUp() {
        AnalyticsProperties analyticsProperties = new AnalyticsProperties();
        PlatformProperties platformProperties = new PlatformProperties();
        platformProperties.setInstallationTier(InstallationTier.TIER_1_CORE);
        FeatureGateService featureGateService = new FeatureGateService(platformProperties, analyticsProperties);
        CostAnalyticsService costAnalyticsService = new TestCostAnalyticsService(
                taskRepository,
                taskEventRepository,
                tokenUsageRepository,
                pricingService,
                null,
                analyticsProperties
        );
        queryService = new QueryService(
                agentRepository,
                taskRepository,
                taskEventRepository,
                tokenUsageRepository,
                pricingService,
                costAnalyticsService,
                null,
                analyticsProperties,
                new com.agentretry.manager.config.AgentMonitorProperties(),
                featureGateService,
                null
        );
    }

    @Test
    void dashboardSummaryCountsRetryTokenCostOncePerTurn() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = task(taskId);
        TokenUsageEntity turn = token(task, 1, 1000, 100, Instant.parse("2026-01-01T12:00:01Z"));

        TaskEventEntity edit = retryEvent(task, TaskEventType.EDIT, "{\"turn\":1}", 1);
        TaskEventEntity testFail = retryEvent(task, TaskEventType.TEST_FAIL, "{\"turn\":1}", 2);

        when(taskRepository.findAll(any(Specification.class))).thenReturn(List.of(task));
        when(tokenUsageRepository.findByTaskIdOrderByTurnIndexAsc(taskId)).thenReturn(List.of(turn));
        when(taskEventRepository.findByTaskIdInAndRetryTrueOrderBySequenceNumAsc(List.of(taskId)))
                .thenReturn(List.of(edit, testFail));

        DashboardSummary summary = queryService.dashboardSummary(null, null, null);

        assertEquals(turn.getEstimatedCostUsd().doubleValue(), summary.retryTokenCostUsd(), 0.0001);
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

    private static TokenUsageEntity token(TaskEntity task, int turnIndex, long input, long output, Instant recordedAt) {
        TokenUsageEntity usage = new TokenUsageEntity();
        usage.setId(UUID.randomUUID());
        usage.setTask(task);
        usage.setTurnIndex(turnIndex);
        usage.setModel("claude-sonnet-4");
        usage.setInputTokens(input);
        usage.setOutputTokens(output);
        usage.setEstimatedCostUsd(new BigDecimal("0.012300"));
        usage.setRecordedAt(recordedAt);
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

    private static final class TestCostAnalyticsService extends CostAnalyticsService {
        TestCostAnalyticsService(
                TaskRepository taskRepository,
                TaskEventRepository taskEventRepository,
                TokenUsageRepository tokenUsageRepository,
                ModelPricingService modelPricingService,
                RollupService rollupService,
                AnalyticsProperties analyticsProperties
        ) {
            super(null, taskRepository, taskEventRepository, tokenUsageRepository, modelPricingService,
                    rollupService, analyticsProperties, null);
        }

        @Override
        public List<LlmRunMetric> llmRunsByToolAndModel(Instant from, Instant to, String user, SourceTool sourceTool) {
            return List.of();
        }
    }
}
