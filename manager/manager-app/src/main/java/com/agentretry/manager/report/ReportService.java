/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.report;

import com.agentretry.contract.SourceTool;
import com.agentretry.manager.domain.TaskEntity;
import com.agentretry.manager.domain.TaskEventEntity;
import com.agentretry.manager.domain.TokenUsageEntity;
import com.agentretry.manager.graphql.CostSummary;
import com.agentretry.manager.graphql.DashboardSummary;
import com.agentretry.manager.graphql.ModelRetryMetric;
import com.agentretry.manager.graphql.ModelRetryRateMetric;
import com.agentretry.manager.graphql.ModelRetryTypeMetric;
import com.agentretry.manager.graphql.RetryAnalysisSummary;
import com.agentretry.manager.graphql.RetryTaskByModelSummary;
import com.agentretry.manager.graphql.TaskConnection;
import com.agentretry.manager.graphql.TimeSeriesPoint;
import com.agentretry.manager.graphql.TokenUsageSummary;
import com.agentretry.manager.graphql.ToolMetric;
import com.agentretry.manager.graphql.UserCostMetric;
import com.agentretry.manager.report.dto.ReportFilters;
import com.agentretry.manager.report.dto.ReportGenerateRequest;
import com.agentretry.manager.service.CostAnalyticsService;
import com.agentretry.manager.service.QueryService;
import com.agentretry.manager.service.RetryAnalyticsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportService {

    private final QueryService queryService;
    private final CostAnalyticsService costAnalyticsService;
    private final RetryAnalyticsService retryAnalyticsService;
    private final JsonReportRenderer jsonReportRenderer;
    private final PdfReportRenderer pdfReportRenderer;

    public ReportService(
            QueryService queryService,
            CostAnalyticsService costAnalyticsService,
            RetryAnalyticsService retryAnalyticsService,
            JsonReportRenderer jsonReportRenderer,
            PdfReportRenderer pdfReportRenderer
    ) {
        this.queryService = queryService;
        this.costAnalyticsService = costAnalyticsService;
        this.retryAnalyticsService = retryAnalyticsService;
        this.jsonReportRenderer = jsonReportRenderer;
        this.pdfReportRenderer = pdfReportRenderer;
    }

    public GeneratedReport generate(ReportGenerateRequest request) {
        return switch (request.dataset()) {
            case EXECUTIVE_SUMMARY -> generateExecutiveSummary(request);
            case TASK_LIST -> generateTaskList(request);
            case TASK_DETAIL -> generateTaskDetail(request);
            case COLLECTOR_ACTIVITY -> generateCollectorActivity(request);
            case RETRY_WASTE -> generateRetryWaste(request);
            case RETRY_BY_MODEL -> generateRetryByModel(request);
            case TOKEN_COST -> generateTokenCost(request);
            default -> throw new ResponseStatusException(
                    HttpStatus.NOT_IMPLEMENTED,
                    request.dataset() + " is not yet implemented"
            );
        };
    }

    private GeneratedReport generateExecutiveSummary(ReportGenerateRequest request) {
        Instant from = resolveFrom(request);
        Instant to = resolveTo(request);
        DashboardSummary summary = queryService.dashboardSummary(from, to, null);

        return switch (request.format()) {
            case CSV -> new GeneratedReport(
                    renderExecutiveSummaryCsv(summary, from, to),
                    "executive-summary.csv",
                    MediaType.parseMediaType("text/csv")
            );
            case JSON -> new GeneratedReport(
                    jsonReportRenderer.render(buildExecutiveSummaryJson(summary, from, to)),
                    "executive-summary.json",
                    MediaType.APPLICATION_JSON
            );
            case PDF -> {
                List<String> headers = List.of("metric", "value");
                List<List<String>> rows = List.of(
                        List.of("totalTasks", String.valueOf(summary.totalTasks())),
                        List.of("avgRetriesPerTask", String.valueOf(summary.avgRetriesPerTask())),
                        List.of("retryTokenCostUsd", String.valueOf(summary.retryTokenCostUsd()))
                );
                yield new GeneratedReport(
                        pdfReportRenderer.render("Executive Summary", headers, rows),
                        "executive-summary.pdf",
                        MediaType.APPLICATION_PDF
                );
            }
            default -> unsupportedFormat(request);
        };
    }

    private GeneratedReport generateCollectorActivity(ReportGenerateRequest request) {
        Instant from = resolveFrom(request);
        Instant to = resolveTo(request);
        UUID agentId = request.filters() != null ? request.filters().agentId() : null;

        List<String> headers = List.of(
                "agentId",
                "hostname",
                "developerEmail",
                "lastHeartbeatAt",
                "installedCollectors",
                "taskCount",
                "retrySum"
        );
        List<List<String>> rows = new ArrayList<>();
        for (var agent : queryService.findAgents()) {
            if (agentId != null && !agent.getId().equals(agentId)) {
                continue;
            }
            TaskConnection connection = queryService.findTasks(
                    agent.getId(), null, from, to, null, null, null, null, null, null, null
            );
            int retrySum = connection.items().stream().mapToInt(TaskEntity::getRetryCount).sum();
            rows.add(List.of(
                    agent.getId().toString(),
                    agent.getHostname() != null ? agent.getHostname() : "",
                    agent.getDeveloperEmail() != null ? agent.getDeveloperEmail() : "",
                    agent.getLastHeartbeatAt() != null ? agent.getLastHeartbeatAt().toString() : "",
                    agent.getInstalledCollectors() != null ? agent.getInstalledCollectors() : "",
                    String.valueOf(connection.totalCount()),
                    String.valueOf(retrySum)
            ));
        }

        return switch (request.format()) {
            case CSV -> new GeneratedReport(
                    CsvReportRenderer.render(headers, rows),
                    "collector-activity.csv",
                    MediaType.parseMediaType("text/csv")
            );
            case PDF -> new GeneratedReport(
                    pdfReportRenderer.render("Collector Activity", headers, rows),
                    "collector-activity.pdf",
                    MediaType.APPLICATION_PDF
            );
            case JSON -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("from", from);
                payload.put("to", to);
                payload.put("headers", headers);
                payload.put("rows", rows);
                yield new GeneratedReport(
                        jsonReportRenderer.render(payload),
                        "collector-activity.json",
                        MediaType.APPLICATION_JSON
                );
            }
            default -> unsupportedFormat(request);
        };
    }

    private GeneratedReport generateTaskList(ReportGenerateRequest request) {
        Instant from = resolveFrom(request);
        Instant to = resolveTo(request);
        SourceTool sourceTool = resolveSourceTool(request.filters());
        UUID agentId = request.filters() != null ? request.filters().agentId() : null;

        TaskConnection connection = queryService.findTasks(
                agentId, sourceTool, from, to, null, null, null, null, null, null, null
        );
        List<String> headers = List.of(
                "id",
                "description",
                "sourceTool",
                "status",
                "retryCount",
                "agentHostname",
                "developerEmail",
                "startedAt",
                "completedAt",
                "estimatedCostUsd"
        );

        List<List<String>> rows = new ArrayList<>();
        for (TaskEntity task : connection.items()) {
            TokenUsageSummary tokenSummary = queryService.summarizeTaskTokens(task.getId());
            rows.add(List.of(
                    task.getId().toString(),
                    task.getDescription(),
                    task.getSourceTool().name(),
                    task.getStatus().name(),
                    String.valueOf(task.getRetryCount()),
                    task.getAgent().getHostname(),
                    task.getAgent().getDeveloperEmail(),
                    String.valueOf(task.getStartedAt()),
                    task.getCompletedAt() != null ? task.getCompletedAt().toString() : "",
                    String.valueOf(tokenSummary.estimatedCostUsd())
            ));
        }

        return new GeneratedReport(
                CsvReportRenderer.render(headers, rows),
                "task-list.csv",
                MediaType.parseMediaType("text/csv")
        );
    }

    private GeneratedReport generateTaskDetail(ReportGenerateRequest request) {
        UUID taskId = request.filters().taskId();
        TaskEntity task = queryService.findTask(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found: " + taskId);
        }

        List<TaskEventEntity> events = queryService.findTaskEvents(taskId);
        List<TokenUsageEntity> tokenUsage = queryService.findTokenUsage(taskId);
        TokenUsageSummary tokenSummary = queryService.summarizeTaskTokens(taskId);

        return switch (request.format()) {
            case CSV -> new GeneratedReport(
                    renderTaskDetailCsv(task, events, tokenUsage, tokenSummary),
                    "task-detail-" + taskId + ".csv",
                    MediaType.parseMediaType("text/csv")
            );
            case JSON -> new GeneratedReport(
                    jsonReportRenderer.render(buildTaskDetailJson(task, events, tokenUsage, tokenSummary)),
                    "task-detail-" + taskId + ".json",
                    MediaType.APPLICATION_JSON
            );
            default -> unsupportedFormat(request);
        };
    }

    private byte[] renderExecutiveSummaryCsv(DashboardSummary summary, Instant from, Instant to) {
        List<String> headers = List.of("section", "metric", "value");
        List<List<String>> rows = new ArrayList<>();

        rows.add(List.of("meta", "from", from.toString()));
        rows.add(List.of("meta", "to", to.toString()));
        rows.add(List.of("summary", "totalTasks", String.valueOf(summary.totalTasks())));
        rows.add(List.of("summary", "avgRetriesPerTask", String.valueOf(summary.avgRetriesPerTask())));
        rows.add(List.of("summary", "retryTokenCostUsd", String.valueOf(summary.retryTokenCostUsd())));

        TokenUsageSummary tokens = summary.totalTokens();
        rows.add(List.of("tokens", "inputTokens", String.valueOf(tokens.inputTokens())));
        rows.add(List.of("tokens", "outputTokens", String.valueOf(tokens.outputTokens())));
        rows.add(List.of("tokens", "cacheReadTokens", String.valueOf(tokens.cacheReadTokens())));
        rows.add(List.of("tokens", "cacheWriteTokens", String.valueOf(tokens.cacheWriteTokens())));
        rows.add(List.of("tokens", "reasoningTokens", String.valueOf(tokens.reasoningTokens())));
        rows.add(List.of("tokens", "estimatedCostUsd", String.valueOf(tokens.estimatedCostUsd())));

        appendMetricRows(rows, "retriesByTool", summary.retriesByTool());
        appendMetricRows(rows, "tokensByTool", summary.tokensByTool());

        for (TimeSeriesPoint point : summary.retryTrend()) {
            rows.add(List.of("retryTrend", point.timestamp(), String.valueOf(point.value())));
        }

        for (TaskEntity task : summary.topRetryTasks()) {
            rows.add(List.of(
                    "topRetryTasks",
                    task.getId().toString(),
                    task.getDescription() + " | retries=" + task.getRetryCount()
            ));
        }

        return CsvReportRenderer.render(headers, rows);
    }

    private void appendMetricRows(List<List<String>> rows, String section, List<ToolMetric> metrics) {
        for (ToolMetric metric : metrics) {
            rows.add(List.of(section, metric.tool(), String.valueOf(metric.value())));
        }
    }

    private Map<String, Object> buildExecutiveSummaryJson(DashboardSummary summary, Instant from, Instant to) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", from);
        payload.put("to", to);
        payload.put("totalTasks", summary.totalTasks());
        payload.put("avgRetriesPerTask", summary.avgRetriesPerTask());
        payload.put("retryTokenCostUsd", summary.retryTokenCostUsd());
        payload.put("totalTokens", summary.totalTokens());
        payload.put("retriesByTool", summary.retriesByTool());
        payload.put("tokensByTool", summary.tokensByTool());
        payload.put("retryTrend", summary.retryTrend());
        payload.put("topRetryTasks", summary.topRetryTasks().stream().map(this::taskSummary).toList());
        return payload;
    }

    private GeneratedReport generateRetryWaste(ReportGenerateRequest request) {
        Instant from = resolveFrom(request);
        Instant to = resolveTo(request);
        SourceTool sourceTool = resolveSourceTool(request.filters());
        CostSummary summary = costAnalyticsService.costSummary(from, to, null, sourceTool);

        return switch (request.format()) {
            case CSV -> new GeneratedReport(
                    renderRetryWasteCsv(summary, from, to),
                    "retry-waste.csv",
                    MediaType.parseMediaType("text/csv")
            );
            case JSON -> new GeneratedReport(
                    jsonReportRenderer.render(buildRetryWasteJson(summary, from, to)),
                    "retry-waste.json",
                    MediaType.APPLICATION_JSON
            );
            default -> unsupportedFormat(request);
        };
    }

    private GeneratedReport generateRetryByModel(ReportGenerateRequest request) {
        Instant from = resolveFrom(request);
        Instant to = resolveTo(request);
        SourceTool sourceTool = resolveSourceTool(request.filters());
        String user = null;
        RetryAnalysisSummary summary = retryAnalyticsService.retryAnalysis(from, to, user, sourceTool);

        return switch (request.format()) {
            case CSV -> new GeneratedReport(
                    renderRetryByModelCsv(summary, from, to),
                    "retry-by-model.csv",
                    MediaType.parseMediaType("text/csv")
            );
            case JSON -> new GeneratedReport(
                    jsonReportRenderer.render(buildRetryByModelJson(summary, from, to)),
                    "retry-by-model.json",
                    MediaType.APPLICATION_JSON
            );
            default -> unsupportedFormat(request);
        };
    }

    private byte[] renderRetryByModelCsv(RetryAnalysisSummary summary, Instant from, Instant to) {
        List<String> headers = List.of("section", "model", "metric", "value");
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("meta", "", "from", from.toString()));
        rows.add(List.of("meta", "", "to", to.toString()));
        rows.add(List.of("summary", "", "unattributedRetryCount", String.valueOf(summary.unattributedRetryCount())));

        appendModelMetricRows(rows, "retriesByModel", summary.retriesByModel());
        appendModelMetricRows(rows, "retryCostByModel", summary.retryCostByModel());
        appendModelMetricRows(rows, "retryWasteByModel", summary.retryWasteByModel());

        for (ModelRetryRateMetric metric : summary.retryRateByModel()) {
            rows.add(List.of("retryRateByModel", metric.model(), "retryRate", String.valueOf(metric.retryRate())));
            rows.add(List.of("retryRateByModel", metric.model(), "totalTurns", String.valueOf(metric.totalTurns())));
        }

        for (ModelRetryTypeMetric metric : summary.retryTypeByModel()) {
            rows.add(List.of(
                    "retryTypeByModel",
                    metric.model(),
                    metric.eventType().name(),
                    String.valueOf(metric.count())
            ));
        }

        for (RetryTaskByModelSummary task : summary.topRetryTasksByModel()) {
            rows.add(List.of(
                    "topRetryTasksByModel",
                    task.model(),
                    task.taskId().toString(),
                    task.description() + " | retries=" + task.retryCount() + " | retryCost=" + task.retryCostUsd()
            ));
        }

        return CsvReportRenderer.render(headers, rows);
    }

    private void appendModelMetricRows(List<List<String>> rows, String section, List<ModelRetryMetric> metrics) {
        for (ModelRetryMetric metric : metrics) {
            rows.add(List.of(section, metric.model(), "value", String.valueOf(metric.value())));
        }
    }

    private Map<String, Object> buildRetryByModelJson(RetryAnalysisSummary summary, Instant from, Instant to) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", from);
        payload.put("to", to);
        payload.put("unattributedRetryCount", summary.unattributedRetryCount());
        payload.put("retriesByModel", summary.retriesByModel());
        payload.put("retryCostByModel", summary.retryCostByModel());
        payload.put("retryWasteByModel", summary.retryWasteByModel());
        payload.put("retryRateByModel", summary.retryRateByModel());
        payload.put("retryTypeByModel", summary.retryTypeByModel());
        payload.put("topRetryTasksByModel", summary.topRetryTasksByModel());
        return payload;
    }

    private GeneratedReport generateTokenCost(ReportGenerateRequest request) {
        Instant from = resolveFrom(request);
        Instant to = resolveTo(request);
        SourceTool sourceTool = resolveSourceTool(request.filters());
        CostSummary summary = costAnalyticsService.costSummary(from, to, null, sourceTool);

        return switch (request.format()) {
            case CSV -> new GeneratedReport(
                    renderTokenCostCsv(summary, from, to),
                    "token-cost.csv",
                    MediaType.parseMediaType("text/csv")
            );
            case JSON -> new GeneratedReport(
                    jsonReportRenderer.render(buildTokenCostJson(summary, from, to)),
                    "token-cost.json",
                    MediaType.APPLICATION_JSON
            );
            default -> unsupportedFormat(request);
        };
    }

    private byte[] renderRetryWasteCsv(CostSummary summary, Instant from, Instant to) {
        List<String> headers = List.of("section", "metric", "value");
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("meta", "from", from.toString()));
        rows.add(List.of("meta", "to", to.toString()));
        rows.add(List.of("summary", "totalCostUsd", String.valueOf(summary.totalCostUsd())));
        rows.add(List.of("summary", "retryCostUsd", String.valueOf(summary.retryCostUsd())));
        rows.add(List.of("summary", "retryWasteRatio", String.valueOf(summary.retryWasteRatio())));

        for (TimeSeriesPoint point : summary.retryCostTrend()) {
            rows.add(List.of("retryCostTrend", point.timestamp(), String.valueOf(point.value())));
        }

        for (var task : summary.topCostlyTasks()) {
            rows.add(List.of(
                    "topCostlyTasks",
                    task.taskId().toString(),
                    task.description() + " | cost=" + task.totalCostUsd() + " | retryCost=" + task.retryCostUsd()
            ));
        }

        return CsvReportRenderer.render(headers, rows);
    }

    private Map<String, Object> buildRetryWasteJson(CostSummary summary, Instant from, Instant to) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", from);
        payload.put("to", to);
        payload.put("totalCostUsd", summary.totalCostUsd());
        payload.put("retryCostUsd", summary.retryCostUsd());
        payload.put("retryWasteRatio", summary.retryWasteRatio());
        payload.put("retryCostTrend", summary.retryCostTrend());
        payload.put("topCostlyTasks", summary.topCostlyTasks());
        return payload;
    }

    private byte[] renderTokenCostCsv(CostSummary summary, Instant from, Instant to) {
        List<String> headers = List.of("section", "key", "value");
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("meta", "from", from.toString()));
        rows.add(List.of("meta", "to", to.toString()));
        rows.add(List.of("summary", "totalCostUsd", String.valueOf(summary.totalCostUsd())));

        appendMetricRows(rows, "costByTool", summary.costByTool());
        appendMetricRows(rows, "costByModel", summary.costByModel());

        for (UserCostMetric user : summary.costByUser()) {
            rows.add(List.of("costByUser", user.developerEmail(), String.valueOf(user.costUsd())));
        }

        for (TimeSeriesPoint point : summary.costTrend()) {
            rows.add(List.of("costTrend", point.timestamp(), String.valueOf(point.value())));
        }

        return CsvReportRenderer.render(headers, rows);
    }

    private Map<String, Object> buildTokenCostJson(CostSummary summary, Instant from, Instant to) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", from);
        payload.put("to", to);
        payload.put("totalCostUsd", summary.totalCostUsd());
        payload.put("costByTool", summary.costByTool());
        payload.put("costByModel", summary.costByModel());
        payload.put("costByUser", summary.costByUser());
        payload.put("costTrend", summary.costTrend());
        return payload;
    }

    private byte[] renderTaskDetailCsv(
            TaskEntity task,
            List<TaskEventEntity> events,
            List<TokenUsageEntity> tokenUsage,
            TokenUsageSummary tokenSummary
    ) {
        List<String> headers = List.of("section", "field", "value");
        List<List<String>> rows = new ArrayList<>();

        rows.add(List.of("task", "id", task.getId().toString()));
        rows.add(List.of("task", "description", task.getDescription()));
        rows.add(List.of("task", "sourceTool", task.getSourceTool().name()));
        rows.add(List.of("task", "status", task.getStatus().name()));
        rows.add(List.of("task", "retryCount", String.valueOf(task.getRetryCount())));
        rows.add(List.of("task", "agentHostname", task.getAgent().getHostname()));
        rows.add(List.of("task", "developerEmail", task.getAgent().getDeveloperEmail()));
        rows.add(List.of("task", "startedAt", task.getStartedAt().toString()));
        rows.add(List.of("task", "completedAt", task.getCompletedAt() != null ? task.getCompletedAt().toString() : ""));

        rows.add(List.of("tokens", "inputTokens", String.valueOf(tokenSummary.inputTokens())));
        rows.add(List.of("tokens", "outputTokens", String.valueOf(tokenSummary.outputTokens())));
        rows.add(List.of("tokens", "estimatedCostUsd", String.valueOf(tokenSummary.estimatedCostUsd())));

        for (TaskEventEntity event : events) {
            rows.add(List.of(
                    "event",
                    String.valueOf(event.getSequenceNum()),
                    event.getEventType().name() + " | retry=" + event.isRetry() + " | at=" + event.getOccurredAt()
            ));
        }

        for (TokenUsageEntity usage : tokenUsage) {
            rows.add(List.of(
                    "tokenTurn",
                    String.valueOf(usage.getTurnIndex()),
                    "model=" + usage.getModel()
                            + " | in=" + usage.getInputTokens()
                            + " | out=" + usage.getOutputTokens()
            ));
        }

        return CsvReportRenderer.render(headers, rows);
    }

    private Map<String, Object> buildTaskDetailJson(
            TaskEntity task,
            List<TaskEventEntity> events,
            List<TokenUsageEntity> tokenUsage,
            TokenUsageSummary tokenSummary
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task", taskSummary(task));
        payload.put("tokenSummary", tokenSummary);
        payload.put("events", events.stream().map(this::eventSummary).toList());
        payload.put("tokenUsage", tokenUsage.stream().map(this::tokenUsageSummary).toList());
        return payload;
    }

    private Map<String, Object> taskSummary(TaskEntity task) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", task.getId());
        summary.put("description", task.getDescription());
        summary.put("sourceTool", task.getSourceTool().name());
        summary.put("status", task.getStatus().name());
        summary.put("retryCount", task.getRetryCount());
        summary.put("agentHostname", task.getAgent().getHostname());
        summary.put("developerEmail", task.getAgent().getDeveloperEmail());
        summary.put("startedAt", task.getStartedAt());
        summary.put("completedAt", task.getCompletedAt());
        return summary;
    }

    private Map<String, Object> eventSummary(TaskEventEntity event) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("sequenceNum", event.getSequenceNum());
        summary.put("eventType", event.getEventType().name());
        summary.put("retry", event.isRetry());
        summary.put("occurredAt", event.getOccurredAt());
        summary.put("payload", event.getPayload());
        return summary;
    }

    private Map<String, Object> tokenUsageSummary(TokenUsageEntity usage) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("turnIndex", usage.getTurnIndex());
        summary.put("model", usage.getModel());
        summary.put("inputTokens", usage.getInputTokens());
        summary.put("outputTokens", usage.getOutputTokens());
        summary.put("cacheReadTokens", usage.getCacheReadTokens());
        summary.put("cacheWriteTokens", usage.getCacheWriteTokens());
        summary.put("reasoningTokens", usage.getReasoningTokens());
        summary.put("estimatedCostUsd", usage.getEstimatedCostUsd());
        return summary;
    }

    private Instant resolveFrom(ReportGenerateRequest request) {
        return request.from() != null ? request.from() : Instant.now().minus(7, ChronoUnit.DAYS);
    }

    private Instant resolveTo(ReportGenerateRequest request) {
        return request.to() != null ? request.to() : Instant.now();
    }

    private SourceTool resolveSourceTool(ReportFilters filters) {
        if (filters == null || filters.sourceTool() == null || filters.sourceTool().isBlank()) {
            return null;
        }
        try {
            return SourceTool.valueOf(filters.sourceTool());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sourceTool: " + filters.sourceTool());
        }
    }

    private GeneratedReport unsupportedFormat(ReportGenerateRequest request) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported format: " + request.format());
    }
}
