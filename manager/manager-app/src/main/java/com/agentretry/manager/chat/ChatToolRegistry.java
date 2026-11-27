/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.chat;

import com.agentretry.contract.SessionReplayFormat;
import com.agentretry.contract.SourceTool;
import com.agentretry.manager.config.ChatProperties;
import com.agentretry.manager.domain.AgentEntity;
import com.agentretry.manager.domain.TaskEntity;
import com.agentretry.manager.graphql.CostSummary;
import com.agentretry.manager.graphql.DashboardSummary;
import com.agentretry.manager.graphql.RetryAnalysisSummary;
import com.agentretry.manager.graphql.TaskConnection;
import com.agentretry.manager.graphql.UserSummary;
import com.agentretry.manager.report.GeneratedReport;
import com.agentretry.manager.report.ReportDataset;
import com.agentretry.manager.report.ReportFormat;
import com.agentretry.manager.report.ReportService;
import com.agentretry.manager.report.dto.ReportFilters;
import com.agentretry.manager.report.dto.ReportGenerateRequest;
import com.agentretry.manager.replay.SessionReplayService;
import com.agentretry.manager.service.CostAnalyticsService;
import com.agentretry.manager.service.QueryService;
import com.agentretry.manager.service.RetryAnalyticsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ChatToolRegistry {

    private final QueryService queryService;
    private final CostAnalyticsService costAnalyticsService;
    private final RetryAnalyticsService retryAnalyticsService;
    private final SessionReplayService sessionReplayService;
    private final ReportService reportService;
    private final ObjectMapper objectMapper;
    private final ChatProperties properties;

    public ChatToolRegistry(
            QueryService queryService,
            CostAnalyticsService costAnalyticsService,
            RetryAnalyticsService retryAnalyticsService,
            SessionReplayService sessionReplayService,
            ReportService reportService,
            ObjectMapper objectMapper,
            ChatProperties properties
    ) {
        this.queryService = queryService;
        this.costAnalyticsService = costAnalyticsService;
        this.retryAnalyticsService = retryAnalyticsService;
        this.sessionReplayService = sessionReplayService;
        this.reportService = reportService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public List<ChatDtos.ToolSpec> specs() {
        List<ChatDtos.ToolSpec> tools = new ArrayList<>();
        tools.add(tool("get_dashboard_summary",
                "Aggregate dashboard metrics (tasks, retries, tokens, cost) for a date range.",
                objectSchema(Map.of(
                        "from", prop("string", "ISO-8601 start instant (optional)"),
                        "to", prop("string", "ISO-8601 end instant (optional)"),
                        "user", prop("string", "Filter by developer email (optional)")
                ))));
        tools.add(tool("get_cost_summary",
                "Token cost analytics including retry waste and top costly tasks.",
                objectSchema(Map.of(
                        "from", prop("string", "ISO-8601 start"),
                        "to", prop("string", "ISO-8601 end"),
                        "user", prop("string", "Developer email filter"),
                        "sourceTool", prop("string", "CURSOR|CLAUDE_CODE|CLAUDE_COWORK|etc")
                ))));
        tools.add(tool("get_retry_analysis",
                "Retry analysis by model: counts, waste, rates, top retry tasks.",
                objectSchema(Map.of(
                        "from", prop("string", "ISO-8601 start"),
                        "to", prop("string", "ISO-8601 end"),
                        "user", prop("string", "Developer email filter"),
                        "sourceTool", prop("string", "Source tool filter")
                ))));
        tools.add(tool("list_tasks",
                "List recent tasks with retries, status, source tool, and git context.",
                objectSchema(Map.of(
                        "from", prop("string", "ISO-8601 start"),
                        "to", prop("string", "ISO-8601 end"),
                        "user", prop("string", "Developer email"),
                        "sourceTool", prop("string", "Source tool"),
                        "minRetries", prop("integer", "Minimum retry count"),
                        "page", prop("integer", "Page number (1-based)"),
                        "size", prop("integer", "Page size (default 10)")
                ))));
        tools.add(tool("get_task",
                "Get a single task by UUID with token summary.",
                objectSchema(Map.of("taskId", prop("string", "Task UUID")), List.of("taskId"))));
        tools.add(tool("get_session_replay_prompt",
                "Get PROMPT-format session replay for a task (redacted for chat).",
                objectSchema(Map.of("taskId", prop("string", "Task UUID")), List.of("taskId"))));
        tools.add(tool("list_agents",
                "List collector agents (hostname, developer, last seen).",
                objectSchema(Map.of())));
        tools.add(tool("list_users",
                "List developers with agent counts.",
                objectSchema(Map.of())));
        tools.add(tool("generate_report_preview",
                "Generate a short JSON report preview for a dataset.",
                objectSchema(Map.of(
                        "dataset", prop("string", "EXECUTIVE_SUMMARY|TASK_LIST|RETRY_WASTE|TOKEN_COST|RETRY_BY_MODEL|COLLECTOR_ACTIVITY"),
                        "from", prop("string", "ISO-8601 start"),
                        "to", prop("string", "ISO-8601 end"),
                        "user", prop("string", "Developer email filter")
                ), List.of("dataset"))));
        return tools;
    }

    public String execute(String name, String argumentsJson, ChatDtos.ChatPageContext pageContext) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            Instant from = parseInstant(text(args, "from"), pageContext == null ? null : pageContext.from(), Instant.now().minus(7, ChronoUnit.DAYS));
            Instant to = parseInstant(text(args, "to"), pageContext == null ? null : pageContext.to(), Instant.now());
            String user = firstNonBlank(text(args, "user"), pageContext == null ? null : pageContext.user());
            SourceTool sourceTool = parseSourceTool(firstNonBlank(text(args, "sourceTool"), pageContext == null ? null : pageContext.sourceTool()));

            String result = switch (name) {
                case "get_dashboard_summary" -> writeJson(summarizeDashboard(queryService.dashboardSummary(from, to, user)));
                case "get_cost_summary" -> writeJson(summarizeCost(costAnalyticsService.costSummary(from, to, user, sourceTool)));
                case "get_retry_analysis" -> writeJson(summarizeRetry(retryAnalyticsService.retryAnalysis(from, to, user, sourceTool)));
                case "list_tasks" -> writeJson(listTasks(args, from, to, user, sourceTool));
                case "get_task" -> writeJson(getTask(args, pageContext));
                case "get_session_replay_prompt" -> getReplay(args, pageContext);
                case "list_agents" -> writeJson(listAgents());
                case "list_users" -> writeJson(listUsers());
                case "generate_report_preview" -> generateReportPreview(args, from, to, user);
                default -> "{\"error\":\"Unknown tool: " + name + "\"}";
            };
            return ChatPrivacy.truncate(ChatPrivacy.redact(result), properties.getMaxToolResultChars());
        } catch (Exception e) {
            return "{\"error\":\"" + escape(e.getMessage()) + "\"}";
        }
    }

    private ObjectNode summarizeDashboard(DashboardSummary s) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("totalTasks", s.totalTasks());
        n.put("avgRetriesPerTask", s.avgRetriesPerTask());
        n.put("retryTokenCostUsd", s.retryTokenCostUsd());
        if (s.totalTokens() != null) {
            n.put("inputTokens", s.totalTokens().inputTokens());
            n.put("outputTokens", s.totalTokens().outputTokens());
            n.put("estimatedCostUsd", s.totalTokens().estimatedCostUsd());
        }
        n.set("retriesByTool", objectMapper.valueToTree(limit(s.retriesByTool(), 8)));
        n.set("tokensByTool", objectMapper.valueToTree(limit(s.tokensByTool(), 8)));
        return n;
    }

    private ObjectNode summarizeCost(CostSummary s) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("totalCostUsd", s.totalCostUsd());
        n.put("retryCostUsd", s.retryCostUsd());
        n.put("retryWasteRatio", s.retryWasteRatio());
        n.set("costByTool", objectMapper.valueToTree(limit(s.costByTool(), 8)));
        n.set("costByModel", objectMapper.valueToTree(limit(s.costByModel(), 8)));
        n.set("costByUser", objectMapper.valueToTree(limit(s.costByUser(), 8)));
        n.set("topCostlyTasks", objectMapper.valueToTree(limit(s.topCostlyTasks(), 5)));
        return n;
    }

    private ObjectNode summarizeRetry(RetryAnalysisSummary s) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("unattributedRetryCount", s.unattributedRetryCount());
        n.set("retriesByModel", objectMapper.valueToTree(limit(s.retriesByModel(), 10)));
        n.set("retryWasteByModel", objectMapper.valueToTree(limit(s.retryWasteByModel(), 10)));
        n.set("retryRateByModel", objectMapper.valueToTree(limit(s.retryRateByModel(), 10)));
        n.set("topRetryTasksByModel", objectMapper.valueToTree(limit(s.topRetryTasksByModel(), 5)));
        return n;
    }

    private ObjectNode listTasks(JsonNode args, Instant from, Instant to, String user, SourceTool sourceTool) {
        int page = args.path("page").asInt(1);
        int size = Math.min(25, Math.max(1, args.path("size").asInt(10)));
        Integer minRetries = args.hasNonNull("minRetries") ? args.get("minRetries").asInt() : null;
        TaskConnection conn = queryService.findTasks(
                null, sourceTool, from, to, minRetries, user, null, null, null, page, size
        );
        ObjectNode n = objectMapper.createObjectNode();
        n.put("totalCount", conn.totalCount());
        ArrayNode items = n.putArray("tasks");
        for (TaskEntity t : conn.items()) {
            items.add(taskNode(t));
        }
        return n;
    }

    private ObjectNode getTask(JsonNode args, ChatDtos.ChatPageContext pageContext) {
        UUID id = resolveTaskId(args, pageContext);
        TaskEntity t = queryService.findTask(id);
        if (t == null) {
            ObjectNode err = objectMapper.createObjectNode();
            err.put("error", "Task not found");
            return err;
        }
        ObjectNode n = taskNode(t);
        n.set("tokenSummary", objectMapper.valueToTree(queryService.summarizeTaskTokens(id)));
        n.put("deepLink", "/tasks/" + id);
        return n;
    }

    private String getReplay(JsonNode args, ChatDtos.ChatPageContext pageContext) {
        UUID id = resolveTaskId(args, pageContext);
        String prompt = sessionReplayService.render(id, SessionReplayFormat.PROMPT);
        ObjectNode n = objectMapper.createObjectNode();
        n.put("taskId", id.toString());
        n.put("deepLink", "/tasks/" + id);
        n.put("promptPreview", ChatPrivacy.truncate(ChatPrivacy.redact(prompt), 6000));
        return writeJson(n);
    }

    private ArrayNode listAgents() {
        ArrayNode arr = objectMapper.createArrayNode();
        for (AgentEntity a : queryService.findAgents()) {
            ObjectNode n = objectMapper.createObjectNode();
            n.put("id", a.getId().toString());
            n.put("name", a.getName());
            n.put("hostname", a.getHostname());
            n.put("developerEmail", a.getDeveloperEmail());
            if (a.getLastHeartbeatAt() != null) {
                n.put("lastHeartbeatAt", a.getLastHeartbeatAt().toString());
            }
            n.put("deepLink", "/agents");
            arr.add(n);
        }
        return arr;
    }

    private ArrayNode listUsers() {
        ArrayNode arr = objectMapper.createArrayNode();
        for (UserSummary u : queryService.findUsers()) {
            ObjectNode n = objectMapper.createObjectNode();
            n.put("developerEmail", u.developerEmail());
            n.put("agentCount", u.agentCount());
            arr.add(n);
        }
        return arr;
    }

    private String generateReportPreview(JsonNode args, Instant from, Instant to, String user) {
        String datasetName = text(args, "dataset");
        ReportDataset dataset = ReportDataset.valueOf(datasetName);
        ReportFilters filters = new ReportFilters(null, null, null);
        GeneratedReport report = reportService.generate(new ReportGenerateRequest(
                dataset, ReportFormat.JSON, from, to, filters, null
        ));
        String body = new String(report.content(), StandardCharsets.UTF_8);
        ObjectNode n = objectMapper.createObjectNode();
        n.put("dataset", dataset.name());
        n.put("filename", report.filename());
        n.put("filterUserHint", user);
        n.put("deepLink", "/reports");
        n.put("preview", ChatPrivacy.truncate(ChatPrivacy.redact(body), 8000));
        return writeJson(n);
    }

    private ObjectNode taskNode(TaskEntity t) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("id", t.getId().toString());
        n.put("description", ChatPrivacy.truncate(ChatPrivacy.redact(t.getDescription()), 240));
        n.put("status", t.getStatus() == null ? null : t.getStatus().name());
        n.put("sourceTool", t.getSourceTool() == null ? null : t.getSourceTool().name());
        n.put("retryCount", t.getRetryCount());
        n.put("startedAt", t.getStartedAt() == null ? null : t.getStartedAt().toString());
        n.put("repoName", t.getRepoName());
        n.put("gitBranch", t.getGitBranch());
        n.put("deepLink", "/tasks/" + t.getId());
        return n;
    }

    private UUID resolveTaskId(JsonNode args, ChatDtos.ChatPageContext pageContext) {
        String raw = firstNonBlank(text(args, "taskId"), pageContext == null ? null : pageContext.taskId());
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("taskId is required");
        }
        return UUID.fromString(raw.trim());
    }

    private static Instant parseInstant(String primary, String fallback, Instant defaultValue) {
        String raw = firstNonBlank(primary, fallback);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Instant.parse(raw);
    }

    private static SourceTool parseSourceTool(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return SourceTool.valueOf(raw.trim().toUpperCase());
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static <T> List<T> limit(List<T> list, int n) {
        if (list == null) return List.of();
        return list.size() <= n ? list : list.subList(0, n);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{\"error\":\"serialize failed\"}";
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "'").replace("\n", " ");
    }

    private static ChatDtos.ToolSpec tool(String name, String description, Map<String, Object> parameters) {
        return new ChatDtos.ToolSpec(name, description, parameters);
    }

    private static Map<String, Object> prop(String type, String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("description", description);
        return m;
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties) {
        return objectSchema(properties, List.of());
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required != null && !required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }
}
