/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.contract.SourceTool;
import com.agentretry.manager.domain.TaskEntity;
import com.agentretry.manager.domain.TaskEventEntity;
import com.agentretry.manager.domain.TokenUsageEntity;
import com.agentretry.manager.graphql.CostSummary;
import com.agentretry.manager.graphql.CostlyTaskSummary;
import com.agentretry.manager.graphql.LlmRunMetric;
import com.agentretry.manager.graphql.TimeSeriesPoint;
import com.agentretry.manager.graphql.ToolMetric;
import com.agentretry.manager.graphql.TrendSeries;
import com.agentretry.manager.graphql.UserCostMetric;
import com.agentretry.manager.clickhouse.ClickHouseCostQueryService;
import com.agentretry.manager.config.AnalyticsProperties;
import com.agentretry.manager.repository.CostAnalyticsRepository;
import com.agentretry.manager.repository.TaskEventRepository;
import com.agentretry.manager.repository.TaskRepository;
import com.agentretry.manager.repository.TaskSpecifications;
import com.agentretry.manager.repository.TokenUsageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CostAnalyticsService {

    private static final int TOP_COSTLY_TASKS_LIMIT = 10;

    private final CostAnalyticsRepository costAnalyticsRepository;
    private final TaskRepository taskRepository;
    private final TaskEventRepository taskEventRepository;
    private final TokenUsageRepository tokenUsageRepository;
    private final ModelPricingService modelPricingService;
    private final RollupService rollupService;
    private final AnalyticsProperties analyticsProperties;
    private final Optional<ClickHouseCostQueryService> clickHouseCostQueryService;

    public CostAnalyticsService(
            CostAnalyticsRepository costAnalyticsRepository,
            TaskRepository taskRepository,
            TaskEventRepository taskEventRepository,
            TokenUsageRepository tokenUsageRepository,
            ModelPricingService modelPricingService,
            RollupService rollupService,
            AnalyticsProperties analyticsProperties,
            @Autowired(required = false) ClickHouseCostQueryService clickHouseCostQueryService
    ) {
        this.costAnalyticsRepository = costAnalyticsRepository;
        this.taskRepository = taskRepository;
        this.taskEventRepository = taskEventRepository;
        this.tokenUsageRepository = tokenUsageRepository;
        this.modelPricingService = modelPricingService;
        this.rollupService = rollupService;
        this.analyticsProperties = analyticsProperties;
        this.clickHouseCostQueryService = Optional.ofNullable(clickHouseCostQueryService);
    }

    public CostSummary costSummary(Instant from, Instant to, String user, SourceTool sourceTool) {
        DateRangeClamp.Range range = DateRangeClamp.clamp(from, to, analyticsProperties);
        Instant resolvedFrom = range.from();
        Instant resolvedTo = range.to();
        String sourceToolName = sourceTool != null ? sourceTool.name() : null;
        boolean useClickHouse = clickHouseCostQueryService.map(ClickHouseCostQueryService::isReadEnabled).orElse(false);

        double totalCostUsd = useClickHouse
                ? clickHouseCostQueryService.flatMap(ch -> ch.sumTotalCost(resolvedFrom, resolvedTo, user, sourceToolName))
                .orElseGet(() -> costAnalyticsRepository.sumTotalCost(resolvedFrom, resolvedTo, user, sourceToolName))
                : costAnalyticsRepository.sumTotalCost(resolvedFrom, resolvedTo, user, sourceToolName);

        List<TaskEntity> tasks = taskRepository.findAll(
                TaskSpecifications.filtered(null, sourceTool, resolvedFrom, resolvedTo, null, user, null, null, null)
        );
        double retryCostUsd = computeRetryTokenCostUsd(tasks);
        double retryWasteRatio = totalCostUsd > 0 ? retryCostUsd / totalCostUsd : 0;

        List<ToolMetric> costByTool = (useClickHouse
                ? clickHouseCostQueryService.flatMap(ch -> ch.aggregateCostByTool(resolvedFrom, resolvedTo, user, sourceToolName))
                .orElseGet(() -> costAnalyticsRepository.aggregateCostByTool(resolvedFrom, resolvedTo, user, sourceToolName))
                : costAnalyticsRepository.aggregateCostByTool(resolvedFrom, resolvedTo, user, sourceToolName))
                .stream()
                .map(entry -> new ToolMetric(entry.getKey(), entry.getValue()))
                .toList();

        List<ToolMetric> costByModel = (useClickHouse
                ? clickHouseCostQueryService.flatMap(ch -> ch.aggregateCostByModel(resolvedFrom, resolvedTo, user, sourceToolName))
                .orElseGet(() -> costAnalyticsRepository.aggregateCostByModel(resolvedFrom, resolvedTo, user, sourceToolName))
                : costAnalyticsRepository.aggregateCostByModel(resolvedFrom, resolvedTo, user, sourceToolName))
                .stream()
                .map(entry -> new ToolMetric(entry.getKey(), entry.getValue()))
                .toList();

        List<LlmRunMetric> llmRunsByToolAndModel = (useClickHouse
                ? clickHouseCostQueryService.flatMap(ch -> ch.aggregateLlmRunsByToolAndModel(resolvedFrom, resolvedTo, user, sourceToolName))
                .orElseGet(() -> costAnalyticsRepository.aggregateLlmRunsByToolAndModel(resolvedFrom, resolvedTo, user, sourceToolName))
                : costAnalyticsRepository.aggregateLlmRunsByToolAndModel(resolvedFrom, resolvedTo, user, sourceToolName))
                .stream()
                .map(row -> new LlmRunMetric(row.sourceTool(), row.model(), row.turnCount(), row.costUsd()))
                .toList();

        List<UserCostMetric> costByUser = (useClickHouse
                ? clickHouseCostQueryService.flatMap(ch -> ch.aggregateCostByUser(resolvedFrom, resolvedTo, user, sourceToolName))
                .orElseGet(() -> costAnalyticsRepository.aggregateCostByUser(resolvedFrom, resolvedTo, user, sourceToolName))
                : costAnalyticsRepository.aggregateCostByUser(resolvedFrom, resolvedTo, user, sourceToolName))
                .stream()
                .map(row -> new UserCostMetric(row.developerEmail(), row.displayName(), row.costUsd()))
                .toList();

        List<TimeSeriesPoint> costTrend;
        List<TrendSeries> costTrendByTool;
        List<TrendSeries> costTrendByModel;
        boolean useRollups = !useClickHouse
                && user == null && sourceTool == null
                && DateRangeClamp.preferRollups(resolvedFrom, resolvedTo, analyticsProperties)
                && rollupService.hasRollups(resolvedFrom, resolvedTo);
        if (useClickHouse) {
            costTrend = TrendSeriesBuilder.fromDayTotals(
                    resolvedFrom,
                    resolvedTo,
                    clickHouseCostQueryService.flatMap(ch -> ch.aggregateCostTrend(resolvedFrom, resolvedTo, user, sourceToolName))
                            .orElseGet(() -> costAnalyticsRepository.aggregateCostTrend(resolvedFrom, resolvedTo, user, sourceToolName))
            );
            costTrendByTool = TrendSeriesBuilder.fromRows(
                    resolvedFrom,
                    resolvedTo,
                    clickHouseCostQueryService.flatMap(ch -> ch.aggregateCostTrendByTool(resolvedFrom, resolvedTo, user, sourceToolName))
                            .orElseGet(() -> costAnalyticsRepository.aggregateCostTrendByTool(resolvedFrom, resolvedTo, user, sourceToolName))
                            .stream()
                            .map(row -> new TrendSeriesBuilder.SeriesDayValue(row.day(), row.series(), row.value()))
                            .toList()
            );
            costTrendByModel = TrendSeriesBuilder.fromRows(
                    resolvedFrom,
                    resolvedTo,
                    clickHouseCostQueryService.flatMap(ch -> ch.aggregateCostTrendByModel(resolvedFrom, resolvedTo, user, sourceToolName))
                            .orElseGet(() -> costAnalyticsRepository.aggregateCostTrendByModel(resolvedFrom, resolvedTo, user, sourceToolName))
                            .stream()
                            .map(row -> new TrendSeriesBuilder.SeriesDayValue(row.day(), row.series(), row.value()))
                            .toList()
            );
        } else if (useRollups) {
            costTrend = rollupService.costTrendTotal(resolvedFrom, resolvedTo);
            costTrendByTool = rollupService.costTrendByTool(resolvedFrom, resolvedTo);
            costTrendByModel = rollupService.costTrendByModel(resolvedFrom, resolvedTo);
        } else {
            costTrend = TrendSeriesBuilder.fromDayTotals(
                    resolvedFrom,
                    resolvedTo,
                    costAnalyticsRepository.aggregateCostTrend(resolvedFrom, resolvedTo, user, sourceToolName)
            );
            costTrendByTool = TrendSeriesBuilder.fromRows(
                    resolvedFrom,
                    resolvedTo,
                    costAnalyticsRepository.aggregateCostTrendByTool(resolvedFrom, resolvedTo, user, sourceToolName).stream()
                            .map(row -> new TrendSeriesBuilder.SeriesDayValue(row.day(), row.series(), row.value()))
                            .toList()
            );
            costTrendByModel = TrendSeriesBuilder.fromRows(
                    resolvedFrom,
                    resolvedTo,
                    costAnalyticsRepository.aggregateCostTrendByModel(resolvedFrom, resolvedTo, user, sourceToolName).stream()
                            .map(row -> new TrendSeriesBuilder.SeriesDayValue(row.day(), row.series(), row.value()))
                            .toList()
            );
        }

        RetryCostTrends retryCostTrends = buildRetryCostTrends(tasks, resolvedFrom, resolvedTo);

        Map<UUID, Double> retryCostByTask = computeRetryCostByTask(tasks);
        List<CostlyTaskSummary> topCostlyTasks = costAnalyticsRepository
                .topCostlyTasks(resolvedFrom, resolvedTo, user, sourceToolName, TOP_COSTLY_TASKS_LIMIT)
                .stream()
                .map(row -> new CostlyTaskSummary(
                        UUID.fromString(row.taskId()),
                        row.description(),
                        row.sourceTool(),
                        row.totalCostUsd(),
                        retryCostByTask.getOrDefault(UUID.fromString(row.taskId()), 0.0),
                        row.retryCount()
                ))
                .toList();

        return new CostSummary(
                totalCostUsd,
                retryCostUsd,
                retryWasteRatio,
                costByTool,
                costByModel,
                llmRunsByToolAndModel,
                costByUser,
                costTrend,
                costTrendByTool,
                costTrendByModel,
                retryCostTrends.total(),
                retryCostTrends.byTool(),
                retryCostTrends.byModel(),
                topCostlyTasks
        );
    }

    public List<LlmRunMetric> llmRunsByToolAndModel(Instant from, Instant to, String user, SourceTool sourceTool) {
        Instant resolvedFrom = from != null ? from : Instant.now().minus(7, ChronoUnit.DAYS);
        Instant resolvedTo = to != null ? to : Instant.now();
        String sourceToolName = sourceTool != null ? sourceTool.name() : null;
        return costAnalyticsRepository.aggregateLlmRunsByToolAndModel(resolvedFrom, resolvedTo, user, sourceToolName)
                .stream()
                .map(row -> new LlmRunMetric(row.sourceTool(), row.model(), row.turnCount(), row.costUsd()))
                .toList();
    }

    public double computeRetryTokenCostUsd(List<TaskEntity> tasks) {
        if (tasks.isEmpty()) {
            return 0;
        }

        List<UUID> taskIds = tasks.stream().map(TaskEntity::getId).toList();
        List<TaskEventEntity> retryEvents = taskEventRepository.findByTaskIdInAndRetryTrueOrderBySequenceNumAsc(taskIds);
        if (retryEvents.isEmpty()) {
            return 0;
        }

        Map<UUID, List<TokenUsageEntity>> tokensByTask = new HashMap<>();
        Set<String> countedKeys = new HashSet<>();
        double retryCost = 0;

        for (TaskEventEntity event : retryEvents) {
            UUID taskId = event.getTask().getId();
            List<TokenUsageEntity> tokenTurns = tokensByTask.computeIfAbsent(
                    taskId,
                    id -> tokenUsageRepository.findByTaskIdOrderByTurnIndexAsc(id)
            );
            Optional<EventTokenUsageMatcher.AttributedTokenUsage> attributed =
                    EventTokenUsageMatcher.attributeUsage(event, tokenTurns, modelPricingService);
            if (attributed.isEmpty() || !countedKeys.add(attributed.get().dedupeKey())) {
                continue;
            }
            Double cost = attributed.get().usage().estimatedCostUsd();
            if (cost != null && cost > 0) {
                retryCost += cost;
            }
        }

        return retryCost;
    }

    public double resolveEstimatedCostUsd(TokenUsageEntity usage) {
        if (usage.getEstimatedCostUsd() != null && usage.getEstimatedCostUsd().signum() > 0) {
            return usage.getEstimatedCostUsd().doubleValue();
        }
        return modelPricingService.estimateCost(
                usage.getModel(),
                usage.getInputTokens(),
                usage.getOutputTokens(),
                usage.getCacheReadTokens(),
                usage.getCacheWriteTokens(),
                usage.getReasoningTokens()
        ).doubleValue();
    }

    private Map<UUID, Double> computeRetryCostByTask(List<TaskEntity> tasks) {
        Map<UUID, Double> retryCostByTask = new HashMap<>();
        if (tasks.isEmpty()) {
            return retryCostByTask;
        }

        List<UUID> taskIds = tasks.stream().map(TaskEntity::getId).toList();
        List<TaskEventEntity> retryEvents = taskEventRepository.findByTaskIdInAndRetryTrueOrderBySequenceNumAsc(taskIds);
        Map<UUID, List<TokenUsageEntity>> tokensByTask = new HashMap<>();
        Map<UUID, Set<String>> countedKeysByTask = new HashMap<>();

        for (TaskEventEntity event : retryEvents) {
            UUID taskId = event.getTask().getId();
            List<TokenUsageEntity> tokenTurns = tokensByTask.computeIfAbsent(
                    taskId,
                    id -> tokenUsageRepository.findByTaskIdOrderByTurnIndexAsc(id)
            );
            Set<String> countedKeys = countedKeysByTask.computeIfAbsent(taskId, ignored -> new HashSet<>());

            Optional<EventTokenUsageMatcher.AttributedTokenUsage> attributed =
                    EventTokenUsageMatcher.attributeUsage(event, tokenTurns, modelPricingService);
            if (attributed.isEmpty() || !countedKeys.add(attributed.get().dedupeKey())) {
                continue;
            }
            Double cost = attributed.get().usage().estimatedCostUsd();
            if (cost != null && cost > 0) {
                retryCostByTask.merge(taskId, cost, Double::sum);
            }
        }

        return retryCostByTask;
    }

    private RetryCostTrends buildRetryCostTrends(List<TaskEntity> tasks, Instant from, Instant to) {
        Map<Instant, Double> totalBuckets = TrendSeriesBuilder.emptyDayBuckets(from, to);
        Map<Instant, Map<String, Double>> byTool = new HashMap<>();
        Map<Instant, Map<String, Double>> byModel = new HashMap<>();

        if (tasks.isEmpty()) {
            return new RetryCostTrends(TrendSeriesBuilder.toPoints(totalBuckets), List.of(), List.of());
        }

        Map<UUID, TaskEntity> taskById = new HashMap<>();
        for (TaskEntity task : tasks) {
            taskById.put(task.getId(), task);
        }

        List<UUID> taskIds = tasks.stream().map(TaskEntity::getId).toList();
        List<TaskEventEntity> retryEvents = taskEventRepository.findByTaskIdInAndRetryTrueOrderBySequenceNumAsc(taskIds);
        Map<UUID, List<TokenUsageEntity>> tokensByTask = new HashMap<>();
        Set<String> countedKeys = new HashSet<>();

        for (TaskEventEntity event : retryEvents) {
            UUID taskId = event.getTask().getId();
            List<TokenUsageEntity> tokenTurns = tokensByTask.computeIfAbsent(
                    taskId,
                    id -> tokenUsageRepository.findByTaskIdOrderByTurnIndexAsc(id)
            );
            Optional<EventTokenUsageMatcher.AttributedTokenUsage> attributed =
                    EventTokenUsageMatcher.attributeUsage(event, tokenTurns, modelPricingService);
            if (attributed.isEmpty() || !countedKeys.add(attributed.get().dedupeKey())) {
                continue;
            }
            Double cost = attributed.get().usage().estimatedCostUsd();
            if (cost == null || cost <= 0) {
                continue;
            }
            Instant day = event.getOccurredAt().truncatedTo(ChronoUnit.DAYS);
            if (day.isBefore(from.truncatedTo(ChronoUnit.DAYS)) || day.isAfter(to.truncatedTo(ChronoUnit.DAYS))) {
                continue;
            }
            totalBuckets.computeIfPresent(day, (key, value) -> value + cost);

            TaskEntity task = taskById.get(taskId);
            String tool = task != null && task.getSourceTool() != null ? task.getSourceTool().name() : "unknown";
            String model = attributed.get().usage().model();
            if (model == null || model.isBlank()
                    || "cursor".equalsIgnoreCase(model.trim())
                    || "unknown".equalsIgnoreCase(model.trim())) {
                model = "unknown";
            } else {
                model = model.trim();
            }
            byTool.computeIfAbsent(day, ignored -> new HashMap<>()).merge(tool, cost, Double::sum);
            byModel.computeIfAbsent(day, ignored -> new HashMap<>()).merge(model, cost, Double::sum);
        }

        return new RetryCostTrends(
                TrendSeriesBuilder.toPoints(totalBuckets),
                TrendSeriesBuilder.fromDaySeriesValues(from, to, byTool),
                TrendSeriesBuilder.fromDaySeriesValues(from, to, byModel)
        );
    }

    private record RetryCostTrends(
            List<TimeSeriesPoint> total,
            List<TrendSeries> byTool,
            List<TrendSeries> byModel
    ) {
    }

    /**
     * Median total session cost for tasks with the same source tool (last 90 days), for cost-outlier evals.
     */
    public double medianTaskCostUsd(SourceTool sourceTool) {
        Instant from = Instant.now().minus(90, ChronoUnit.DAYS);
        Instant to = Instant.now();
        List<TaskEntity> tasks = taskRepository.findAll(TaskSpecifications.filtered(
                null, sourceTool, from, to, null, null, null, null, null
        ));
        if (tasks.isEmpty()) {
            return 0;
        }
        List<Double> costs = new ArrayList<>();
        for (TaskEntity task : tasks) {
            double cost = tokenUsageRepository.findByTaskIdOrderByTurnIndexAsc(task.getId()).stream()
                    .mapToDouble(u -> u.getEstimatedCostUsd() != null ? u.getEstimatedCostUsd().doubleValue() : 0)
                    .sum();
            if (cost > 0) {
                costs.add(cost);
            }
        }
        if (costs.isEmpty()) {
            return 0;
        }
        costs.sort(Double::compareTo);
        int mid = costs.size() / 2;
        if (costs.size() % 2 == 0) {
            return (costs.get(mid - 1) + costs.get(mid)) / 2.0;
        }
        return costs.get(mid);
    }
}
