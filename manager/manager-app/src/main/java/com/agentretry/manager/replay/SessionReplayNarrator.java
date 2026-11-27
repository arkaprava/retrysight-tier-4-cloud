/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.replay;

import com.agentretry.manager.graphql.EventTokenUsage;
import com.agentretry.manager.domain.TokenUsageEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SessionReplayNarrator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SessionReplayNarrator() {
    }

    static String describeEvent(com.agentretry.manager.domain.TaskEventEntity event) {
        JsonNode payload = parsePayload(event.getPayload());
        String summary = summarizePayload(event.getEventType().name(), payload);
        if (summary != null && !summary.isBlank()) {
            return summary;
        }
        return event.getEventType().name().replace('_', ' ').toLowerCase();
    }

    static String describeTokenTurn(TokenUsageEntity turn) {
        return "Token turn " + turn.getTurnIndex()
                + " on " + (turn.getModel() != null ? turn.getModel() : "unknown model")
                + " — " + formatTokenCounts(turn);
    }

    static Map<String, Object> detailsFromPayload(String payloadJson) {
        JsonNode payload = parsePayload(payloadJson);
        if (payload == null || payload.isNull()) {
            return Map.of();
        }

        Map<String, Object> details = new LinkedHashMap<>();
        putIfPresent(details, "action", text(payload, "action"));
        putIfPresent(details, "filePath", text(payload, "filePath"));
        putIfPresent(details, "tool", text(payload, "tool"));
        putIfPresent(details, "changeSummary", text(payload, "changeSummary"));
        putIfPresent(details, "changeType", text(payload, "changeType"));
        putIfPresent(details, "reason", text(payload, "reason"));
        putIfPresent(details, "userFeedback", text(payload, "userFeedback"));
        putIfPresent(details, "context", text(payload, "context"));
        putIfPresent(details, "command", text(payload, "command"));
        putIfPresent(details, "status", text(payload, "status"));
        putIfPresent(details, "oldPreview", text(payload, "oldPreview"));
        putIfPresent(details, "newPreview", text(payload, "newPreview"));
        putIfPresent(details, "patchPreview", text(payload, "patchPreview"));
        putIfPresent(details, "outputPreview", text(payload, "outputPreview"));

        if (payload.has("editOrdinal") && payload.get("editOrdinal").isNumber()) {
            details.put("editOrdinal", payload.get("editOrdinal").asInt());
        }
        if (payload.has("turn") && payload.get("turn").isNumber()) {
            details.put("turn", payload.get("turn").asInt());
        }
        if (payload.has("exitCode") && payload.get("exitCode").isNumber()) {
            details.put("exitCode", payload.get("exitCode").asInt());
        }
        if (payload.has("linesAdded") && payload.get("linesAdded").isNumber()) {
            details.put("linesAdded", payload.get("linesAdded").asInt());
        }
        if (payload.has("linesRemoved") && payload.get("linesRemoved").isNumber()) {
            details.put("linesRemoved", payload.get("linesRemoved").asInt());
        }
        if (payload.has("affectedFiles") && payload.get("affectedFiles").isArray()) {
            List<String> files = new ArrayList<>();
            payload.get("affectedFiles").forEach(node -> files.add(node.asText()));
            if (!files.isEmpty()) {
                details.put("affectedFiles", files);
            }
        }

        return details;
    }

    static String formatTokenUsage(EventTokenUsage usage) {
        List<String> parts = new ArrayList<>();
        if (usage.inputTokens() > 0) {
            parts.add(usage.inputTokens() + " in");
        }
        if (usage.outputTokens() > 0) {
            parts.add(usage.outputTokens() + " out");
        }
        if (usage.cacheReadTokens() > 0) {
            parts.add(usage.cacheReadTokens() + " cache read");
        }
        if (usage.cacheWriteTokens() > 0) {
            parts.add(usage.cacheWriteTokens() + " cache write");
        }
        if (usage.reasoningTokens() > 0) {
            parts.add(usage.reasoningTokens() + " reasoning");
        }
        if (usage.estimatedCostUsd() != null && usage.estimatedCostUsd() > 0) {
            parts.add(String.format("$%.4f", usage.estimatedCostUsd()));
        }
        return String.join(" / ", parts);
    }

    private static String summarizePayload(String eventType, JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return null;
        }

        String changeSummary = text(payload, "changeSummary");
        if (changeSummary != null) {
            return changeSummary;
        }

        List<String> parts = new ArrayList<>();
        String action = text(payload, "action");
        if (action != null) {
            parts.add(action.replace('_', ' '));
        }
        String reason = text(payload, "reason");
        if (reason != null) {
            parts.add(reason);
        }
        String filePath = text(payload, "filePath");
        if (filePath != null) {
            parts.add(filePath);
        }
        String tool = text(payload, "tool");
        if (tool != null) {
            parts.add("tool: " + tool);
        }
        String command = text(payload, "command");
        if (command != null) {
            parts.add(command);
        }
        if (payload.has("turn") && payload.get("turn").isNumber()) {
            parts.add("turn " + payload.get("turn").asInt());
        }
        String status = text(payload, "status");
        if (status != null) {
            parts.add(status);
        }
        if (payload.has("exitCode") && payload.get("exitCode").isNumber()) {
            parts.add("exit " + payload.get("exitCode").asInt());
        }

        if (!parts.isEmpty()) {
            return String.join(" · ", parts);
        }

        if ("SESSION_START".equals(eventType)) {
            return "Session started";
        }
        if ("SESSION_END".equals(eventType)) {
            return "Session ended";
        }
        if ("COMPACTION".equals(eventType)) {
            return "Context compaction occurred";
        }

        return null;
    }

    private static String formatTokenCounts(TokenUsageEntity turn) {
        return turn.getInputTokens() + " in / " + turn.getOutputTokens() + " out";
    }

    private static JsonNode parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(payloadJson);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String text(JsonNode payload, String field) {
        if (!payload.has(field) || payload.get(field).isNull()) {
            return null;
        }
        String value = payload.get(field).asText();
        return value.isBlank() ? null : value;
    }

    private static void putIfPresent(Map<String, Object> details, String key, String value) {
        if (value != null && !value.isBlank()) {
            details.put(key, value);
        }
    }
}
