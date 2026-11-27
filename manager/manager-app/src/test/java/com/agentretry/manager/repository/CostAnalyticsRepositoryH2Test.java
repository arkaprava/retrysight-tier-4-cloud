/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.repository;

import com.agentretry.contract.SourceTool;
import com.agentretry.contract.TaskStatus;
import com.agentretry.manager.domain.AgentEntity;
import com.agentretry.manager.domain.TaskEntity;
import com.agentretry.manager.domain.TokenUsageEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.agentretry.manager.persistence.DatabaseSqlDialect;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DataJpaTest
@Import({CostAnalyticsRepository.class, DatabaseSqlDialect.class})
@ActiveProfiles("dev-h2")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:cost_analytics_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.liquibase.enabled=true"
})
class CostAnalyticsRepositoryH2Test {

    @Autowired
    private CostAnalyticsRepository costAnalyticsRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TokenUsageRepository tokenUsageRepository;

    private Instant from;
    private Instant to;

    @BeforeEach
    void seedCostData() {
        from = Instant.parse("2026-01-01T00:00:00Z");
        to = Instant.parse("2026-01-02T00:00:00Z");

        AgentEntity agent = new AgentEntity();
        agent.setId(UUID.randomUUID());
        agent.setName("dev-agent");
        agent.setDeveloperEmail("dev@example.com");
        agent.setDisplayName("Dev User");
        agent.setHostname("dev-1");
        agent.setCreatedAt(from);
        agentRepository.save(agent);

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setAgent(agent);
        task.setExternalSessionId("session-1");
        task.setSourceTool(SourceTool.CURSOR);
        task.setDescription("Fix dashboard");
        task.setStatus(TaskStatus.COMPLETED);
        task.setRetryCount(1);
        task.setStartedAt(from.plusSeconds(60));
        taskRepository.save(task);

        tokenUsageRepository.save(token(task, 1, "claude-sonnet-4", "0.010000", from.plusSeconds(120)));
        tokenUsageRepository.save(token(task, 2, "gpt-4o", "0.020000", from.plusSeconds(180)));
    }

    @Test
    void aggregateQueriesUseH2SafeColumnAliases() {
        assertEquals(0.03, costAnalyticsRepository.sumTotalCost(from, to, null, null), 0.0001);

        List<Map.Entry<String, Double>> byTool = costAnalyticsRepository.aggregateCostByTool(from, to, null, null);
        assertFalse(byTool.isEmpty());
        assertEquals(SourceTool.CURSOR.name(), byTool.getFirst().getKey());

        List<Map.Entry<String, Double>> byModel = costAnalyticsRepository.aggregateCostByModel(from, to, null, null);
        assertEquals(2, byModel.size());

        List<CostAnalyticsRepository.UserCostRow> byUser =
                costAnalyticsRepository.aggregateCostByUser(from, to, null, null);
        assertEquals(1, byUser.size());
        assertEquals("dev@example.com", byUser.getFirst().developerEmail());

        List<Map.Entry<Instant, Double>> trend = costAnalyticsRepository.aggregateCostTrend(from, to, null, null);
        assertFalse(trend.isEmpty());

        List<CostAnalyticsRepository.LlmRunRow> llmRuns =
                costAnalyticsRepository.aggregateLlmRunsByToolAndModel(from, to, null, null);
        assertEquals(2, llmRuns.size());

        List<CostAnalyticsRepository.TaskCostRow> topTasks =
                costAnalyticsRepository.topCostlyTasks(from, to, null, null, 5);
        assertEquals(1, topTasks.size());
        assertEquals(0.03, topTasks.getFirst().totalCostUsd(), 0.0001);
    }

    private static TokenUsageEntity token(
            TaskEntity task,
            int turnIndex,
            String model,
            String costUsd,
            Instant recordedAt
    ) {
        TokenUsageEntity usage = new TokenUsageEntity();
        usage.setId(UUID.randomUUID());
        usage.setTask(task);
        usage.setTurnIndex(turnIndex);
        usage.setModel(model);
        usage.setInputTokens(1000);
        usage.setOutputTokens(100);
        usage.setEstimatedCostUsd(new BigDecimal(costUsd));
        usage.setRecordedAt(recordedAt);
        usage.setIdempotencyKey(task.getId() + "-turn-" + turnIndex);
        return usage;
    }
}
