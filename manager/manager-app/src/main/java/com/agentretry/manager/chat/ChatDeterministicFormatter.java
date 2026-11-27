/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Turns tool JSON into short, readable markdown for deterministic (no-Ollama) mode.
 */
final class ChatDeterministicFormatter {

    private final ObjectMapper objectMapper;

    ChatDeterministicFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String format(String toolName, String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson == null || rawJson.isBlank() ? "{}" : rawJson);
            if (root.hasNonNull("error")) {
                return "**" + pretty(toolName) + "**\n\n- Error: " + root.get("error").asText() + "\n";
            }
            return switch (toolName) {
                case "get_dashboard_summary" -> formatDashboard(root);
                case "get_cost_summary" -> formatCost(root);
                case "get_retry_analysis" -> formatRetry(root);
                case "list_tasks" -> formatTasks(root);
                case "get_task" -> formatTask(root);
                case "list_agents" -> formatAgents(root);
                case "list_users" -> formatUsers(root);
                case "get_session_replay_prompt" -> formatReplay(root);
                case "generate_report_preview" -> formatReport(root);
                default -> "**" + pretty(toolName) + "**\n\n```json\n"
                        + ChatPrivacy.truncate(rawJson, 1200) + "\n```\n";
            };
        } catch (Exception e) {
            return "**" + pretty(toolName) + "**\n\n```json\n"
                    + ChatPrivacy.truncate(rawJson, 1200) + "\n```\n";
        }
    }

    private String formatDashboard(JsonNode n) {
        StringBuilder sb = new StringBuilder("**Dashboard summary**\n\n");
        sb.append("- Tasks: ").append(n.path("totalTasks").asInt()).append('\n');
        sb.append("- Avg retries / task: ").append(fmtNum(n.path("avgRetriesPerTask").asDouble())).append('\n');
        sb.append("- Estimated cost: $").append(fmtMoney(n.path("estimatedCostUsd").asDouble())).append('\n');
        sb.append("- Retry token cost: $").append(fmtMoney(n.path("retryTokenCostUsd").asDouble())).append('\n');
        appendMetricList(sb, "Retries by tool", n.path("retriesByTool"), "tool", "value", false);
        appendMetricList(sb, "Tokens by tool", n.path("tokensByTool"), "tool", "value", false);
        return sb.append('\n').toString();
    }

    private String formatCost(JsonNode n) {
        StringBuilder sb = new StringBuilder("**Cost summary**\n\n");
        sb.append("- Total cost: $").append(fmtMoney(n.path("totalCostUsd").asDouble())).append('\n');
        sb.append("- Retry cost: $").append(fmtMoney(n.path("retryCostUsd").asDouble())).append('\n');
        sb.append("- Retry waste: ").append(fmtPct(n.path("retryWasteRatio").asDouble())).append('\n');
        appendUserCosts(sb, n.path("costByUser"));
        appendMetricList(sb, "Cost by tool", n.path("costByTool"), "tool", "value", true);
        appendMetricList(sb, "Cost by model", n.path("costByModel"), "tool", "value", true);
        appendTopTasks(sb, n.path("topCostlyTasks"));
        return sb.append('\n').toString();
    }

    private String formatRetry(JsonNode n) {
        StringBuilder sb = new StringBuilder("**Retry analysis**\n\n");
        sb.append("- Unattributed retries: ").append(n.path("unattributedRetryCount").asInt()).append('\n');
        appendMetricList(sb, "Retries by model", n.path("retriesByModel"), "tool", "value", false);
        appendMetricList(sb, "Retry waste by model", n.path("retryWasteByModel"), "tool", "value", true);
        return sb.append('\n').toString();
    }

    private String formatTasks(JsonNode n) {
        StringBuilder sb = new StringBuilder("**Tasks**\n\n");
        sb.append("- Matching tasks: ").append(n.path("totalCount").asInt()).append('\n');
        JsonNode tasks = n.path("tasks");
        if (tasks.isArray() && !tasks.isEmpty()) {
            sb.append("\nTop results:\n");
            int i = 0;
            for (JsonNode t : tasks) {
                if (i++ >= 5) break;
                String id = t.path("id").asText("");
                String shortId = id.length() > 8 ? id.substring(0, 8) : id;
                sb.append("- [").append(shortId).append("…](").append(t.path("deepLink").asText("/tasks"))
                        .append(") ")
                        .append(t.path("sourceTool").asText("?"))
                        .append(" · retries ").append(t.path("retryCount").asInt())
                        .append(" — ").append(t.path("description").asText("")).append('\n');
            }
        }
        return sb.append('\n').toString();
    }

    private String formatTask(JsonNode n) {
        StringBuilder sb = new StringBuilder("**Task**\n\n");
        sb.append("- [Open task](").append(n.path("deepLink").asText("/tasks")).append(")\n");
        sb.append("- Status: ").append(n.path("status").asText("—")).append('\n');
        sb.append("- Tool: ").append(n.path("sourceTool").asText("—")).append('\n');
        sb.append("- Retries: ").append(n.path("retryCount").asInt()).append('\n');
        sb.append("- Description: ").append(n.path("description").asText("")).append('\n');
        return sb.append('\n').toString();
    }

    private String formatAgents(JsonNode n) {
        StringBuilder sb = new StringBuilder("**Collectors**\n\n");
        if (!n.isArray() || n.isEmpty()) {
            return sb.append("- No agents found.\n\n").toString();
        }
        int i = 0;
        for (JsonNode a : n) {
            if (i++ >= 8) break;
            sb.append("- ").append(a.path("hostname").asText("?"))
                    .append(" · ").append(a.path("developerEmail").asText("—"))
                    .append(" · last seen ").append(a.path("lastHeartbeatAt").asText("—")).append('\n');
        }
        sb.append("\nSee [Collectors](/agents).\n");
        return sb.append('\n').toString();
    }

    private String formatUsers(JsonNode n) {
        StringBuilder sb = new StringBuilder("**Developers**\n\n");
        if (!n.isArray() || n.isEmpty()) {
            return sb.append("- No users found.\n\n").toString();
        }
        int i = 0;
        for (JsonNode u : n) {
            if (i++ >= 10) break;
            sb.append("- ").append(u.path("developerEmail").asText("—"))
                    .append(" · agents ").append(u.path("agentCount").asInt()).append('\n');
        }
        return sb.append('\n').toString();
    }

    private String formatReplay(JsonNode n) {
        StringBuilder sb = new StringBuilder("**Session replay**\n\n");
        sb.append("- [Open task](").append(n.path("deepLink").asText("/tasks")).append(")\n");
        String preview = n.path("promptPreview").asText("");
        if (!preview.isBlank()) {
            sb.append("\nPreview:\n\n```\n").append(ChatPrivacy.truncate(preview, 900)).append("\n```\n");
        }
        return sb.append('\n').toString();
    }

    private String formatReport(JsonNode n) {
        StringBuilder sb = new StringBuilder("**Report preview**\n\n");
        sb.append("- Dataset: ").append(n.path("dataset").asText("—")).append('\n');
        sb.append("- File: ").append(n.path("filename").asText("—")).append('\n');
        sb.append("- [Reports](/reports)\n");
        return sb.append('\n').toString();
    }

    private void appendUserCosts(StringBuilder sb, JsonNode users) {
        if (!users.isArray() || users.isEmpty()) {
            return;
        }
        sb.append("\nSpend by user:\n");
        int i = 0;
        for (JsonNode u : users) {
            if (i++ >= 5) break;
            String email = firstText(u, "developerEmail", "user", "name");
            double cost = firstDouble(u, "costUsd", "value");
            sb.append("- ").append(email.isBlank() ? "—" : email)
                    .append(": $").append(fmtMoney(cost)).append('\n');
        }
    }

    private void appendTopTasks(StringBuilder sb, JsonNode tasks) {
        if (!tasks.isArray() || tasks.isEmpty()) {
            return;
        }
        sb.append("\nTop costly tasks:\n");
        int i = 0;
        for (JsonNode t : tasks) {
            if (i++ >= 5) break;
            String id = t.path("taskId").asText(t.path("id").asText(""));
            String link = id.isBlank() ? "/tasks" : "/tasks/" + id;
            String shortId = id.length() > 8 ? id.substring(0, 8) + "…" : id;
            sb.append("- [").append(shortId.isBlank() ? "task" : shortId).append("](").append(link).append(") $")
                    .append(fmtMoney(firstDouble(t, "totalCostUsd", "costUsd", "value"))).append('\n');
        }
    }

    private void appendMetricList(
            StringBuilder sb,
            String title,
            JsonNode arr,
            String labelKey,
            String valueKey,
            boolean money
    ) {
        if (!arr.isArray() || arr.isEmpty()) {
            return;
        }
        sb.append('\n').append(title).append(":\n");
        int i = 0;
        for (JsonNode item : arr) {
            if (i++ >= 5) break;
            String label = firstText(item, labelKey, "name", "model");
            double value = firstDouble(item, valueKey, "costUsd");
            sb.append("- ").append(label.isBlank() ? "—" : label).append(": ");
            if (money) {
                sb.append('$').append(fmtMoney(value));
            } else {
                sb.append(fmtNum(value));
            }
            sb.append('\n');
        }
    }

    private static String firstText(JsonNode n, String... keys) {
        for (String key : keys) {
            if (n.hasNonNull(key)) {
                return n.get(key).asText("");
            }
        }
        return "";
    }

    private static double firstDouble(JsonNode n, String... keys) {
        for (String key : keys) {
            if (n.has(key) && n.get(key).isNumber()) {
                return n.get(key).asDouble();
            }
        }
        return 0;
    }

    private static String pretty(String tool) {
        return tool.replace('_', ' ');
    }

    private static String fmtMoney(double v) {
        return String.format(java.util.Locale.US, "%.2f", v);
    }

    private static String fmtNum(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.05) {
            return String.format(java.util.Locale.US, "%.0f", v);
        }
        return String.format(java.util.Locale.US, "%.2f", v);
    }

    private static String fmtPct(double ratio) {
        return String.format(java.util.Locale.US, "%.1f%%", ratio * 100.0);
    }
}
