/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.contract.SourceTool;
import com.agentretry.manager.domain.AgentEntity;
import com.agentretry.manager.domain.TaskEntity;
import com.agentretry.manager.domain.TaskEventEntity;
import com.agentretry.manager.domain.TokenUsageEntity;
import com.agentretry.manager.eval.EvalAnalyticsService;
import com.agentretry.manager.graphql.DashboardSummary;
import com.agentretry.manager.graphql.EvalSummary;
import com.agentretry.manager.platform.FeatureGateService;
import com.agentretry.manager.graphql.EventTokenUsage;
import com.agentretry.manager.graphql.LlmRunMetric;
import com.agentretry.manager.graphql.TaskConnection;
import com.agentretry.manager.graphql.TimeSeriesPoint;
import com.agentretry.manager.graphql.TokenUsageSummary;
import com.agentretry.manager.graphql.ToolMetric;
import com.agentretry.manager.graphql.TrendSeries;
import com.agentretry.manager.graphql.UserSummary;
import com.agentretry.manager.config.AgentMonitorProperties;
import com.agentretry.manager.config.AnalyticsProperties;
import com.agentretry.manager.repository.AgentRepository;
import com.agentretry.manager.repository.TaskEventRepository;
import com.agentretry.manager.repository.TaskRepository;
import com.agentretry.manager.repository.TaskSpecifications;
import com.agentretry.manager.repository.TokenUsageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class QueryService {

    private final AgentRepository agentRepository;
    private final TaskRepository taskRepository;
    private final TaskEventRepository taskEventRepository;
    private final TokenUsageRepository tokenUsageRepository;
    private final ModelPricingService modelPricingService;
    private final CostAnalyticsService costAnalyticsService;
    private final RollupService rollupService;
    private final AnalyticsProperties analyticsProperties;
    private final AgentMonitorProperties agentMonitorProperties;
    private final FeatureGateService featureGateService;
    private final EvalAnalyticsService evalAnalyticsService;

    public QueryService(
            AgentRepository agentRepository,
            TaskRepository taskRepository,
            TaskEventRepository taskEventRepository,
            TokenUsageRepository tokenUsageRepository,
            ModelPricingService modelPricingService,
            CostAnalyticsService costAnalyticsService,
            RollupService rollupService,
            AnalyticsProperties analyticsProperties,
            AgentMonitorProperties agentMonitorProperties,
            FeatureGateService featureGateService,
            EvalAnalyticsService evalAnalyticsService
    ) {
        this.agentRepository = agentRepository;
        this.taskRepository = taskRepository;
        this.taskEventRepository = taskEventRepository;
        this.tokenUsageRepository = tokenUsageRepository;
        this.modelPricingService = modelPricingService;
        this.costAnalyticsService = costAnalyticsService;
        this.rollupService = rollupService;
        this.analyticsProperties = analyticsProperties;
        this.agentMonitorProperties = agentMonitorProperties;
        this.featureGateService = featureGateService;
        this.evalAnalyticsService = evalAnalyticsService;
    }

    public List<AgentEntity> findAgents() {
        return agentRepository.findAll();
    }

    public List<AgentEntity> findStaleAgents() {
        return findAgents().stream().filter(this::isAgentStale).toList();
    }

    public boolean isAgentStale(AgentEntity agent) {
        if (agent == null) {
            return true;
        }
        Instant last = agent.getLastHeartbeatAt();
        if (last == null) {
            return true;
        }
        int threshold = Math.max(1, agentMonitorProperties.getStaleAfterSeconds());
        return last.isBefore(Instant.now().minus(threshold, ChronoUnit.SECONDS));
    }

    public AgentEntity findAgent(UUID id) {
        return agentRepository.findById(id).orElse(null);
    }

    public List<UserSummary> findUsers() {
        Map<String, UserAccumulator> accumulators = new java.util.LinkedHashMap<>();
        for (AgentEntity agent : agentRepository.findAll()) {
            String email = agent.getDeveloperEmail() != null ? agent.getDeveloperEmail() : "unknown";
            String key = email.toLowerCase();
            accumulators.computeIfAbsent(key, ignored -> new UserAccumulator(email))
                    .add(agent);
        }
        return accumulators.values().stream()
                .map(UserAccumulator::toSummary)
                .sorted(Comparator.comparing(UserSummary::developerEmail, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public TaskConnection findTasks(
            UUID agentId,
            SourceTool sourceTool,
            Instant from,
            Instant to,
            Integer minRetries,
            String user,
            String repo,
            String branch,
            String remoteHost,
            Integer page,
            Integer size
    ) {
        var spec = TaskSpecifications.filtered(
                agentId, sourceTool, from, to, minRetries, user, repo, branch, remoteHost
        );

        if (page == null && size == null) {
            List<TaskEntity> tasks = taskRepository.findAll(spec);
            return new TaskConnection(tasks, tasks.size());
        }

        int pageNum = page != null && page > 0 ? page : 1;
        int pageSize = size != null && size > 0 ? size : 15;
        long totalCount = taskRepository.count(spec);
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize);
        List<TaskEntity> tasks = taskRepository.findAll(spec, pageable).getContent();
        return new TaskConnection(tasks, (int) totalCount);
    }

    public TaskEntity findTask(UUID id) {
        return taskRepository.findByIdWithAgent(id).orElse(null);
    }

    public List<TaskEventEntity> findTaskEvents(UUID taskId) {
        return taskEventRepository.findByTaskIdOrderBySequenceNumAsc(taskId);
    }

    public List<TokenUsageEntity> findTokenUsage(UUID taskId) {
        return tokenUsageRepository.findByTaskIdOrderByTurnIndexAsc(taskId);
    }

    public Map<TaskEventEntity, EventTokenUsage> resolveEventTokenUsage(List<TaskEventEntity> events) {
        Map<UUID, List<TokenUsageEntity>> tokensByTask = new HashMap<>();
        Map<TaskEventEntity, EventTokenUsage> resolved = new HashMap<>();

        for (TaskEventEntity event : events) {
            UUID taskId = event.getTask().getId();
            List<TokenUsageEntity> tokenTurns = tokensByTask.computeIfAbsent(
                    taskId,
                    this::findTokenUsage
            );
            EventTokenUsage usage = EventTokenUsageMatcher.match(event, tokenTurns, modelPricingService);
            if (usage != null) {
                resolved.put(event, usage);
            }
        }

        return resolved;
    }

    public TokenUsageSummary summarizeTaskTokens(UUID taskId) {
        List<TokenUsageEntity> usages = findTokenUsage(taskId);
        long input = 0;
        long output = 0;
        long cacheRead = 0;
        long cacheWrite = 0;
        long reasoning = 0;
        double cost = 0;

        for (TokenUsageEntity usage : usages) {
            input += usage.getInputTokens();
            output += usage.getOutputTokens();
            cacheRead += usage.getCacheReadTokens();
            cacheWrite += usage.getCacheWriteTokens();
            reasoning += usage.getReasoningTokens();
            cost += costAnalyticsService.resolveEstimatedCostUsd(usage);
        }

        return new TokenUsageSummary(input, output, cacheRead, cacheWrite, reasoning, cost);
    }

    private double resolveEstimatedCostUsd(TokenUsageEntity usage) {
        return costAnalyticsService.resolveEstimatedCostUsd(usage);
    }

    public DashboardSummary dashboardSummary(Instant from, Instant to, String user) {
        DateRangeClamp.Range range = DateRangeClamp.clamp(from, to, analyticsProperties);
        from = range.from();
        to = range.to();
        List<TaskEntity> tasks = taskRepository.findAll(TaskSpecifications.filtered(
                null, null, from, to, null, user, null, null, null
        ));
        int totalTasks = tasks.size();
        double avgRetries = tasks.stream().mapToInt(TaskEntity::getRetryCount).average().orElse(0);

        long totalInput = 0;
        long totalOutput = 0;
        long totalCacheRead = 0;
        long totalCacheWrite = 0;
        long totalReasoning = 0;
        double totalCost = 0;

        Map<SourceTool, Integer> retriesByTool = new EnumMap<>(SourceTool.class);
        Map<SourceTool, Long> tokensByTool = new EnumMap<>(SourceTool.class);

        for (TaskEntity task : tasks) {
            retriesByTool.merge(task.getSourceTool(), task.getRetryCount(), Integer::sum);
            TokenUsageSummary summary = summarizeTaskTokens(task.getId());
            long taskTokens = summary.inputTokens() + summary.outputTokens() + summary.cacheReadTokens()
                    + summary.cacheWriteTokens() + summary.reasoningTokens();
            tokensByTool.merge(task.getSourceTool(), taskTokens, Long::sum);

            totalInput += summary.inputTokens();
            totalOutput += summary.outputTokens();
            totalCacheRead += summary.cacheReadTokens();
            totalCacheWrite += summary.cacheWriteTokens();
            totalReasoning += summary.reasoningTokens();
            totalCost += summary.estimatedCostUsd();
        }

        List<ToolMetric> retryMetrics = retriesByTool.entrySet().stream()
                .map(e -> new ToolMetric(e.getKey().name(), e.getValue().doubleValue()))
                .toList();

        List<ToolMetric> tokenMetrics = tokensByTool.entrySet().stream()
                .map(e -> new ToolMetric(e.getKey().name(), e.getValue().doubleValue()))
                .toList();

        Instant trendFrom = from;
        Instant trendTo = to;
        RetryTrends retryTrends;
        if (user == null && DateRangeClamp.preferRollups(trendFrom, trendTo, analyticsProperties)
                && rollupService.hasRollups(trendFrom, trendTo)) {
            retryTrends = new RetryTrends(
                    rollupService.retryTrendTotal(trendFrom, trendTo),
                    rollupService.retryTrendByTool(trendFrom, trendTo),
                    rollupService.retryTrendByModel(trendFrom, trendTo)
            );
        } else {
            retryTrends = buildRetryTrends(tasks, trendFrom, trendTo);
        }

        List<TaskEntity> topRetryTasks = tasks.stream()
                .sorted(Comparator.comparingInt(TaskEntity::getRetryCount).reversed())
                .limit(10)
                .toList();

        double retryTokenCostUsd = costAnalyticsService.computeRetryTokenCostUsd(tasks);

        List<LlmRunMetric> llmRunsByToolAndModel = costAnalyticsService.llmRunsByToolAndModel(from, to, user, null);

        EvalSummary evalSummary = null;
        List<TimeSeriesPoint> evalPassRateTrend = List.of();
        if (featureGateService.isEvaluationsEnabled()) {
            evalSummary = evalAnalyticsService.summary(from, to);
            evalPassRateTrend = evalAnalyticsService.passRateTrend(from, to);
        }

        return new DashboardSummary(
                totalTasks,
                avgRetries,
                new TokenUsageSummary(totalInput, totalOutput, totalCacheRead, totalCacheWrite, totalReasoning, totalCost),
                retryTokenCostUsd,
                retryMetrics,
                tokenMetrics,
                llmRunsByToolAndModel,
                retryTrends.total(),
                retryTrends.byTool(),
                retryTrends.byModel(),
                topRetryTasks,
                evalSummary,
                evalPassRateTrend
        );
    }

    public Map<TaskEntity, List<String>> resolveModelsUsed(List<TaskEntity> tasks) {
        if (tasks.isEmpty()) {
            return Map.of();
        }
        List<UUID> taskIds = tasks.stream().map(TaskEntity::getId).toList();
        List<TokenUsageEntity> turns = tokenUsageRepository.findByTaskIdInOrderByTaskIdAscTurnIndexAsc(taskIds);
        Map<UUID, Set<String>> modelsByTaskId = new HashMap<>();
        for (TokenUsageEntity turn : turns) {
            UUID taskId = turn.getTask().getId();
            String model = turn.getModel();
            if (model == null || model.isBlank() || isPlaceholderModel(model)) {
                continue;
            }
            modelsByTaskId.computeIfAbsent(taskId, ignored -> new LinkedHashSet<>()).add(model.trim());
        }
        Map<TaskEntity, List<String>> result = new HashMap<>();
        for (TaskEntity task : tasks) {
            Set<String> models = modelsByTaskId.getOrDefault(task.getId(), Set.of());
            result.put(task, List.copyOf(models));
        }
        return result;
    }

    public Map<TaskEntity, String> resolveLatestModels(List<TaskEntity> tasks) {
        if (tasks.isEmpty()) {
            return Map.of();
        }
        List<UUID> taskIds = tasks.stream().map(TaskEntity::getId).toList();
        List<TokenUsageEntity> turns = tokenUsageRepository.findByTaskIdInOrderByTaskIdAscTurnIndexAsc(taskIds);
        Map<UUID, String> latestByTaskId = new HashMap<>();
        for (TokenUsageEntity turn : turns) {
            UUID taskId = turn.getTask().getId();
            String model = turn.getModel();
            if (model != null && !model.isBlank() && !isPlaceholderModel(model)) {
                latestByTaskId.put(taskId, model.trim());
            }
        }
        Map<TaskEntity, String> result = new HashMap<>();
        for (TaskEntity task : tasks) {
            result.put(task, latestByTaskId.get(task.getId()));
        }
        return result;
    }

    private RetryTrends buildRetryTrends(List<TaskEntity> tasks, Instant from, Instant to) {
        Map<Instant, Double> totalBuckets = TrendSeriesBuilder.emptyDayBuckets(from, to);
        Map<Instant, Map<String, Double>> byTool = new HashMap<>();

        for (TaskEntity task : tasks) {
            if (task.getStartedAt() == null || task.getRetryCount() <= 0) {
                continue;
            }
            Instant day = task.getStartedAt().truncatedTo(ChronoUnit.DAYS);
            if (!totalBuckets.containsKey(day)) {
                continue;
            }
            double retries = task.getRetryCount();
            totalBuckets.computeIfPresent(day, (ignored, value) -> value + retries);
            String tool = task.getSourceTool() != null ? task.getSourceTool().name() : "unknown";
            byTool.computeIfAbsent(day, ignored -> new HashMap<>()).merge(tool, retries, Double::sum);
        }

        Map<Instant, Map<String, Double>> byModel = buildRetryTrendByModel(tasks, from, to);

        return new RetryTrends(
                TrendSeriesBuilder.toPoints(totalBuckets),
                TrendSeriesBuilder.fromDaySeriesValues(from, to, byTool),
                TrendSeriesBuilder.fromDaySeriesValues(from, to, byModel)
        );
    }

    private Map<Instant, Map<String, Double>> buildRetryTrendByModel(List<TaskEntity> tasks, Instant from, Instant to) {
        Map<Instant, Map<String, Double>> byModel = new HashMap<>();
        if (tasks.isEmpty()) {
            return byModel;
        }

        List<UUID> taskIds = tasks.stream().map(TaskEntity::getId).toList();
        Map<UUID, List<TokenUsageEntity>> tokensByTask = new HashMap<>();
        List<TaskEventEntity> retryEvents =
                taskEventRepository.findByTaskIdInAndRetryTrueOrderBySequenceNumAsc(taskIds);

        Instant start = from.truncatedTo(ChronoUnit.DAYS);
        Instant end = to.truncatedTo(ChronoUnit.DAYS);

        for (TaskEventEntity event : retryEvents) {
            Instant day = event.getOccurredAt().truncatedTo(ChronoUnit.DAYS);
            if (day.isBefore(start) || day.isAfter(end)) {
                continue;
            }
            UUID taskId = event.getTask().getId();
            List<TokenUsageEntity> tokenTurns = tokensByTask.computeIfAbsent(
                    taskId,
                    id -> tokenUsageRepository.findByTaskIdOrderByTurnIndexAsc(id)
            );
            var attributed = EventTokenUsageMatcher.attributeUsage(event, tokenTurns, modelPricingService);
            String model = attributed
                    .map(usage -> {
                        String name = usage.usage().model();
                        return name == null || name.isBlank() || isPlaceholderModel(name) ? "unknown" : name.trim();
                    })
                    .orElse("unknown");
            byModel.computeIfAbsent(day, ignored -> new HashMap<>()).merge(model, 1.0, Double::sum);
        }
        return byModel;
    }

    private record RetryTrends(
            List<TimeSeriesPoint> total,
            List<TrendSeries> byTool,
            List<TrendSeries> byModel
    ) {
    }

    private static boolean isPlaceholderModel(String model) {
        String normalized = model.trim().toLowerCase();
        return "cursor".equals(normalized) || "unknown".equals(normalized);
    }

    private static final class UserAccumulator {
        private final String developerEmail;
        private String displayName;
        private String osUsername;
        private String gitEmail;
        private int agentCount;

        private UserAccumulator(String developerEmail) {
            this.developerEmail = developerEmail;
        }

        private void add(AgentEntity agent) {
            agentCount++;
            if (displayName == null && agent.getDisplayName() != null && !agent.getDisplayName().isBlank()) {
                displayName = agent.getDisplayName();
            }
            if (osUsername == null && agent.getOsUsername() != null && !agent.getOsUsername().isBlank()) {
                osUsername = agent.getOsUsername();
            }
            if (gitEmail == null && agent.getGitEmail() != null && !agent.getGitEmail().isBlank()) {
                gitEmail = agent.getGitEmail();
            }
        }

        private UserSummary toSummary() {
            return new UserSummary(developerEmail, displayName, osUsername, gitEmail, agentCount);
        }
    }
}
