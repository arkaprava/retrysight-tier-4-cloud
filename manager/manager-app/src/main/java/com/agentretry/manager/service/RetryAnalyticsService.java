/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.contract.SourceTool;
import com.agentretry.contract.TaskEventType;
import com.agentretry.manager.domain.TaskEntity;
import com.agentretry.manager.domain.TaskEventEntity;
import com.agentretry.manager.domain.TokenUsageEntity;
import com.agentretry.manager.graphql.ModelRetryMetric;
import com.agentretry.manager.graphql.ModelRetryRateMetric;
import com.agentretry.manager.graphql.ModelRetryTypeMetric;
import com.agentretry.manager.graphql.RetryAnalysisSummary;
import com.agentretry.manager.graphql.RetryTaskByModelSummary;
import com.agentretry.manager.repository.TaskEventRepository;
import com.agentretry.manager.repository.TaskRepository;
import com.agentretry.manager.repository.TaskSpecifications;
import com.agentretry.manager.repository.TokenUsageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RetryAnalyticsService {

    private static final String UNKNOWN_MODEL = "unknown";
    private static final int TOP_TASKS_LIMIT = 10;

    private final TaskRepository taskRepository;
    private final TaskEventRepository taskEventRepository;
    private final TokenUsageRepository tokenUsageRepository;
    private final ModelPricingService modelPricingService;
    private final CostAnalyticsService costAnalyticsService;

    public RetryAnalyticsService(
            TaskRepository taskRepository,
            TaskEventRepository taskEventRepository,
            TokenUsageRepository tokenUsageRepository,
            ModelPricingService modelPricingService,
            CostAnalyticsService costAnalyticsService
    ) {
        this.taskRepository = taskRepository;
        this.taskEventRepository = taskEventRepository;
        this.tokenUsageRepository = tokenUsageRepository;
        this.modelPricingService = modelPricingService;
        this.costAnalyticsService = costAnalyticsService;
    }

    public RetryAnalysisSummary retryAnalysis(Instant from, Instant to, String user, SourceTool sourceTool) {
        Instant resolvedFrom = from != null ? from : Instant.now().minus(7, ChronoUnit.DAYS);
        Instant resolvedTo = to != null ? to : Instant.now();

        List<TaskEntity> tasks = taskRepository.findAll(
                TaskSpecifications.filtered(null, sourceTool, resolvedFrom, resolvedTo, null, user, null, null, null)
        );
        if (tasks.isEmpty()) {
            return RetryAnalysisSummary.empty();
        }

        List<UUID> taskIds = tasks.stream().map(TaskEntity::getId).toList();
        Map<UUID, TaskEntity> taskById = new HashMap<>();
        for (TaskEntity task : tasks) {
            taskById.put(task.getId(), task);
        }

        Map<String, Integer> retriesByModel = new HashMap<>();
        Map<String, Double> retryCostByModel = new HashMap<>();
        Map<String, Double> totalCostByModel = new HashMap<>();
        Map<String, Integer> totalTurnsByModel = new HashMap<>();
        Map<String, Map<TaskEventType, Integer>> retryTypeByModel = new HashMap<>();
        Map<String, Map<UUID, TaskModelStats>> taskStatsByModel = new HashMap<>();

        Map<UUID, List<TokenUsageEntity>> tokensByTask = new HashMap<>();
        for (TaskEntity task : tasks) {
            List<TokenUsageEntity> turns = tokenUsageRepository.findByTaskIdOrderByTurnIndexAsc(task.getId());
            tokensByTask.put(task.getId(), turns);
            for (TokenUsageEntity turn : turns) {
                String model = normalizeModel(turn.getModel());
                totalTurnsByModel.merge(model, 1, Integer::sum);
                totalCostByModel.merge(model, costAnalyticsService.resolveEstimatedCostUsd(turn), Double::sum);
            }
        }

        List<TaskEventEntity> retryEvents =
                taskEventRepository.findByTaskIdInAndRetryTrueOrderBySequenceNumAsc(taskIds);
        Set<String> countedCostKeys = new HashSet<>();
        int unattributedRetryCount = 0;

        for (TaskEventEntity event : retryEvents) {
            UUID taskId = event.getTask().getId();
            List<TokenUsageEntity> tokenTurns = tokensByTask.getOrDefault(taskId, List.of());

            var attributed = EventTokenUsageMatcher.attributeUsage(event, tokenTurns, modelPricingService);
            String model = attributed
                    .map(usage -> normalizeModel(usage.usage().model()))
                    .orElse(UNKNOWN_MODEL);

            if (attributed.isEmpty()) {
                unattributedRetryCount++;
            }

            retriesByModel.merge(model, 1, Integer::sum);
            retryTypeByModel
                    .computeIfAbsent(model, ignored -> new HashMap<>())
                    .merge(event.getEventType(), 1, Integer::sum);

            TaskModelStats taskStats = taskStatsByModel
                    .computeIfAbsent(model, ignored -> new HashMap<>())
                    .computeIfAbsent(taskId, ignored -> new TaskModelStats());
            taskStats.retryCount++;

            if (attributed.isPresent() && countedCostKeys.add(attributed.get().dedupeKey())) {
                Double cost = attributed.get().usage().estimatedCostUsd();
                if (cost != null && cost > 0) {
                    retryCostByModel.merge(model, cost, Double::sum);
                    taskStats.retryCostUsd += cost;
                }
            }
        }

        Map<String, Double> retryWasteByModel = new HashMap<>();
        Set<String> allModels = new HashSet<>();
        allModels.addAll(totalCostByModel.keySet());
        allModels.addAll(retryCostByModel.keySet());
        for (String model : allModels) {
            double totalCost = totalCostByModel.getOrDefault(model, 0.0);
            double retryCost = retryCostByModel.getOrDefault(model, 0.0);
            double wasteRatio = totalCost > 0 ? retryCost / totalCost : 0;
            retryWasteByModel.put(model, wasteRatio);
        }

        List<ModelRetryRateMetric> retryRateByModel = new ArrayList<>();
        Set<String> rateModels = new HashSet<>();
        rateModels.addAll(retriesByModel.keySet());
        rateModels.addAll(totalTurnsByModel.keySet());
        for (String model : rateModels) {
            int retries = retriesByModel.getOrDefault(model, 0);
            int turns = totalTurnsByModel.getOrDefault(model, 0);
            double rate = turns > 0 ? (double) retries / turns : 0;
            retryRateByModel.add(new ModelRetryRateMetric(model, rate, turns));
        }

        List<ModelRetryTypeMetric> retryTypeMetrics = new ArrayList<>();
        for (Map.Entry<String, Map<TaskEventType, Integer>> entry : retryTypeByModel.entrySet()) {
            for (Map.Entry<TaskEventType, Integer> typeEntry : entry.getValue().entrySet()) {
                retryTypeMetrics.add(new ModelRetryTypeMetric(entry.getKey(), typeEntry.getKey(), typeEntry.getValue()));
            }
        }

        List<RetryTaskByModelSummary> topRetryTasksByModel = buildTopRetryTasks(taskStatsByModel, taskById);

        return new RetryAnalysisSummary(
                sortMetrics(retriesByModel),
                sortMetrics(retryCostByModel),
                sortMetrics(retryWasteByModel),
                retryRateByModel.stream()
                        .sorted(Comparator.comparingDouble(ModelRetryRateMetric::retryRate).reversed())
                        .toList(),
                retryTypeMetrics.stream()
                        .sorted(Comparator
                                .comparing(ModelRetryTypeMetric::model)
                                .thenComparing(metric -> metric.eventType().name()))
                        .toList(),
                topRetryTasksByModel,
                unattributedRetryCount
        );
    }

    private List<RetryTaskByModelSummary> buildTopRetryTasks(
            Map<String, Map<UUID, TaskModelStats>> taskStatsByModel,
            Map<UUID, TaskEntity> taskById
    ) {
        List<RetryTaskByModelSummary> summaries = new ArrayList<>();
        for (Map.Entry<String, Map<UUID, TaskModelStats>> modelEntry : taskStatsByModel.entrySet()) {
            for (Map.Entry<UUID, TaskModelStats> taskEntry : modelEntry.getValue().entrySet()) {
                TaskEntity task = taskById.get(taskEntry.getKey());
                if (task == null) {
                    continue;
                }
                TaskModelStats stats = taskEntry.getValue();
                summaries.add(new RetryTaskByModelSummary(
                        modelEntry.getKey(),
                        task.getId(),
                        task.getDescription(),
                        task.getSourceTool().name(),
                        stats.retryCount,
                        stats.retryCostUsd
                ));
            }
        }
        return summaries.stream()
                .sorted(Comparator
                        .comparingDouble(RetryTaskByModelSummary::retryCostUsd)
                        .reversed()
                        .thenComparing(RetryTaskByModelSummary::retryCount, Comparator.reverseOrder()))
                .limit(TOP_TASKS_LIMIT)
                .toList();
    }

    private List<ModelRetryMetric> sortMetrics(Map<String, ? extends Number> values) {
        return values.entrySet().stream()
                .sorted((left, right) -> Double.compare(
                        right.getValue().doubleValue(),
                        left.getValue().doubleValue()))
                .map(entry -> new ModelRetryMetric(entry.getKey(), entry.getValue().doubleValue()))
                .toList();
    }

    private String normalizeModel(String model) {
        if (model == null || model.isBlank()) {
            return UNKNOWN_MODEL;
        }
        return modelPricingService.normalizeModel(model);
    }

    private static final class TaskModelStats {
        int retryCount;
        double retryCostUsd;
    }
}
