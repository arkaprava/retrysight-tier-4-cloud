/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.collector;

import com.agentretry.agent.config.AgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EventPayloadBuilder {

    private static final int PREVIEW_LIMIT = 200;
    private static final int CONTEXT_LIMIT = 160;
    private static final int SUMMARY_LIMIT = 240;

    private final AgentProperties properties;
    private final Map<String, String> lastUserContext = new ConcurrentHashMap<>();

    public EventPayloadBuilder(AgentProperties properties) {
        this.properties = properties;
    }

    public void recordUserContext(String sessionKey, String message) {
        if (sessionKey == null || message == null || message.isBlank()) {
            return;
        }
        lastUserContext.put(sessionKey, truncate(message.trim(), contextLimit()));
    }

    public String userContextPreview(String sessionKey) {
        return lastUserContext.get(sessionKey);
    }

    public Map<String, Object> editEvent(
            String sessionKey,
            String tool,
            String filePath,
            int priorEditCount,
            boolean retry,
            JsonNode input
    ) {
        Map<String, Object> payload = basePayload();
        payload.put("action", retry ? "rewrite" : priorEditCount == 0 ? "initial_edit" : "edit");
        payload.put("tool", tool);
        payload.put("filePath", filePath);
        payload.put("editOrdinal", priorEditCount + 1);
        enrichEditPayload(payload, input);

        String context = userContextPreview(sessionKey);
        if (context != null) {
            payload.put("context", context);
        }
        return payload;
    }

    public Map<String, Object> diffAccepted(
            String sessionKey,
            int turn,
            String status,
            JsonNode node,
            List<String> affectedFiles
    ) {
        Map<String, Object> payload = basePayload();
        payload.put("action", "accepted");
        payload.put("turn", turn);
        payload.put("status", status);
        addOptionalText(payload, "reason", node, "reason", "message", "summary");
        addAffectedFiles(payload, affectedFiles);
        addUserContext(payload, sessionKey);
        return payload;
    }

    public Map<String, Object> diffRejected(
            String sessionKey,
            int turn,
            String status,
            JsonNode node,
            List<String> affectedFiles
    ) {
        Map<String, Object> payload = basePayload();
        payload.put("action", "rejected");
        payload.put("turn", turn);
        payload.put("status", status);
        addOptionalText(payload, "reason", node, "reason", "rejection_reason", "error", "message", "summary");
        addOptionalText(payload, "userFeedback", node, "user_feedback", "feedback", "comment");
        addAffectedFiles(payload, affectedFiles);
        addUserContext(payload, sessionKey);
        return payload;
    }

    public Map<String, Object> testFailure(String command, int exitCode, String outputPreview, String sessionKey) {
        Map<String, Object> payload = basePayload();
        payload.put("action", "test_failed");
        payload.put("command", truncate(command, 200));
        payload.put("exitCode", exitCode);
        if (outputPreview != null && !outputPreview.isBlank()) {
            payload.put("outputPreview", truncate(outputPreview, previewLimit()));
        }
        addUserContext(payload, sessionKey);
        return payload;
    }

    public String resolveFilePath(JsonNode input) {
        if (input == null || input.isMissingNode()) {
            return "unknown";
        }
        for (String field : List.of("path", "file_path", "target_file", "filePath", "relative_path")) {
            if (input.has(field) && !input.get(field).asText("").isBlank()) {
                return input.get(field).asText();
            }
        }
        return "unknown";
    }

    private Map<String, Object> basePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        return payload;
    }

    private void enrichEditPayload(Map<String, Object> payload, JsonNode input) {
        if (input == null || input.isMissingNode()) {
            return;
        }

        String oldText = firstText(input, "old_string", "old_str", "oldString", "search", "old_text");
        String newText = firstText(input, "new_string", "new_str", "newString", "replace", "new_text", "content");
        String patch = firstText(input, "patch", "diff", "unified_diff");
        String content = firstText(input, "content", "contents", "text", "body");

        if (oldText != null) {
            payload.put("oldPreview", truncate(oldText, previewLimit()));
        }
        if (newText != null) {
            payload.put("newPreview", truncate(newText, previewLimit()));
        } else if (content != null) {
            payload.put("newPreview", truncate(content, previewLimit()));
        }
        if (patch != null) {
            payload.put("patchPreview", truncate(patch, previewLimit()));
            int[] lineStats = countPatchLines(patch);
            payload.put("linesAdded", lineStats[0]);
            payload.put("linesRemoved", lineStats[1]);
        }

        if (oldText != null && newText != null) {
            payload.put("changeType", "search_replace");
            payload.put("changeSummary", truncate(
                    "Replace \"" + abbreviate(oldText) + "\" with \"" + abbreviate(newText) + "\" in "
                            + payload.get("filePath"),
                    SUMMARY_LIMIT
            ));
        } else if (patch != null) {
            payload.put("changeType", "patch");
            payload.put("changeSummary", truncate(
                    "Apply patch to " + payload.get("filePath") + formatLineStats(payload),
                    SUMMARY_LIMIT
            ));
        } else if (content != null || newText != null) {
            String written = content != null ? content : newText;
            payload.put("changeType", "write");
            payload.put("changeSummary", truncate(
                    "Write " + written.length() + " chars to " + payload.get("filePath"),
                    SUMMARY_LIMIT
            ));
            payload.put("linesAdded", countLines(written));
        } else {
            payload.put("changeType", payload.get("tool"));
            payload.put("changeSummary", "Edit " + payload.get("filePath") + " via " + payload.get("tool"));
        }
    }

    private void addUserContext(Map<String, Object> payload, String sessionKey) {
        String context = userContextPreview(sessionKey);
        if (context != null) {
            payload.put("context", context);
        }
    }

    private void addAffectedFiles(Map<String, Object> payload, List<String> affectedFiles) {
        if (affectedFiles != null && !affectedFiles.isEmpty()) {
            payload.put("affectedFiles", affectedFiles);
        }
    }

    private void addOptionalText(Map<String, Object> payload, String key, JsonNode node, String... fields) {
        if (node == null || node.isMissingNode()) {
            return;
        }
        String value = firstText(node, fields);
        if (value != null && !value.isBlank()) {
            payload.put(key, truncate(value, SUMMARY_LIMIT));
        }
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field) && node.get(field).isTextual()) {
                String value = node.get(field).asText("").trim();
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private String abbreviate(String value) {
        String trimmed = value.replace('\n', ' ').trim();
        return trimmed.length() <= 40 ? trimmed : trimmed.substring(0, 37) + "…";
    }

    private String formatLineStats(Map<String, Object> payload) {
        Object added = payload.get("linesAdded");
        Object removed = payload.get("linesRemoved");
        if (added == null && removed == null) {
            return "";
        }
        return " (+" + (added != null ? added : 0) + "/-" + (removed != null ? removed : 0) + " lines)";
    }

    private int[] countPatchLines(String patch) {
        int added = 0;
        int removed = 0;
        for (String line : patch.split("\n")) {
            if (line.startsWith("+") && !line.startsWith("+++")) {
                added++;
            } else if (line.startsWith("-") && !line.startsWith("---")) {
                removed++;
            }
        }
        return new int[]{added, removed};
    }

    private int countLines(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.split("\n", -1).length;
    }

    private int previewLimit() {
        return properties.getPrivacy().isStoreFullPrompts() ? 2000 : PREVIEW_LIMIT;
    }

    private int contextLimit() {
        return properties.getPrivacy().isStoreFullPrompts() ? 1000 : CONTEXT_LIMIT;
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    public static List<String> distinctFiles(List<String> files) {
        List<String> distinct = new ArrayList<>();
        for (String file : files) {
            if (file != null && !file.isBlank() && !distinct.contains(file)) {
                distinct.add(file);
            }
        }
        return distinct;
    }
}
