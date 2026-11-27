/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.collector;

import com.agentretry.agent.config.AgentProperties;
import com.agentretry.agent.detector.RetryDetector;
import com.agentretry.agent.git.ProjectContextEnricher;
import com.agentretry.agent.uploader.EventBuffer;
import com.agentretry.contract.SessionTaskIds;
import com.agentretry.contract.SourceTool;
import com.agentretry.contract.TaskCompleteRequest;
import com.agentretry.contract.TaskEventType;
import com.agentretry.contract.TaskStartRequest;
import com.agentretry.contract.TaskStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared JSONL collector for tier-2 IDE surfaces that emit role-based or typed events.
 */
public class StandardJsonlCollector extends AbstractSessionCollector implements ConfigTypedCollector {

    private static final Set<String> EDIT_EVENT_TYPES = Set.of(
            "file_edit", "file_create", "write", "edit", "apply_patch", "search_replace"
    );

    private final EventBuffer eventBuffer;
    private final RetryDetector retryDetector;
    private final ObjectMapper objectMapper;
    private final EventPayloadBuilder payloadBuilder;
    private final ProjectContextEnricher projectContextEnricher;
    private final SourceTool sourceTool;
    private final String configType;
    private final String defaultWatchPath;
    private final String defaultModel;
    private final Map<String, UUID> sessionTasks = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> fileEditCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> retryCounts = new ConcurrentHashMap<>();
    private final Set<String> startedSessions = ConcurrentHashMap.newKeySet();

    public StandardJsonlCollector(
            AgentProperties properties,
            EventBuffer eventBuffer,
            RetryDetector retryDetector,
            ObjectMapper objectMapper,
            EventPayloadBuilder payloadBuilder,
            ProjectContextEnricher projectContextEnricher,
            SourceTool sourceTool,
            String configType,
            String defaultWatchPath
    ) {
        super(properties);
        this.eventBuffer = eventBuffer;
        this.retryDetector = retryDetector;
        this.objectMapper = objectMapper;
        this.payloadBuilder = payloadBuilder;
        this.projectContextEnricher = projectContextEnricher;
        this.sourceTool = sourceTool;
        this.configType = configType;
        this.defaultWatchPath = defaultWatchPath;
        this.defaultModel = configType;
    }

    @Override
    public SourceTool sourceTool() {
        return sourceTool;
    }

    @Override
    public String configType() {
        return configType;
    }

    @Override
    public boolean isEnabled() {
        return isCollectorEnabled(configType);
    }

    @Override
    public List<Path> watchPaths() {
        return resolveWatchPaths(configType, defaultWatchPath);
    }

    @Override
    public void scan() {
        for (Path root : watchPaths()) {
            fileWatcher.scanDirectory(root, ".jsonl", this::parseLine);
        }
    }

    private void parseLine(String sessionKey, String line) {
        try {
            JsonNode node = objectMapper.readTree(line);
            if (node.has("type")) {
                handleTypedEvent(sessionKey, node);
                return;
            }

            String role = node.path("role").asText();
            if ("user".equals(role)) {
                handleUserMessage(sessionKey, node);
            } else if ("assistant".equals(role)) {
                handleAssistantMessage(sessionKey, node);
            }
        } catch (Exception ignored) {
        }
    }

    private void handleTypedEvent(String sessionKey, JsonNode node) {
        String type = node.get("type").asText().toLowerCase();
        UUID taskId = sessionTasks.computeIfAbsent(sessionKey, key -> SessionTaskIds.forSession(sourceTool, key));

        switch (type) {
            case "user_message", "user.message", "user" -> handleUserMessage(sessionKey, node);
            case "assistant_message", "assistant.message", "assistant" -> handleAssistantMessage(sessionKey, node);
            case "file_edit", "file_create", "write", "edit", "apply_patch" -> handleFileEdit(sessionKey, taskId, node);
            case "terminal_command", "shell", "bash" -> handleTerminalCommand(sessionKey, taskId, node);
            case "compaction", "compaction_summary", "context_compaction" ->
                    eventBuffer.enqueueTaskEvent(taskId, TaskEventType.COMPACTION, false, Map.of());
            case "subagent", "sub_agent", "task_call" ->
                    eventBuffer.enqueueTaskEvent(taskId, TaskEventType.SUBAGENT, false, Map.of());
            case "session_end", "session.end", "task_completed" -> completeSession(sessionKey);
            default -> {
            }
        }
    }

    private void handleUserMessage(String sessionKey, JsonNode node) {
        UUID taskId = sessionTasks.computeIfAbsent(sessionKey, key -> SessionTaskIds.forSession(sourceTool, key));
        String description = extractText(node);
        if (description.isBlank()) {
            return;
        }

        if (!startedSessions.contains(sessionKey)) {
            startedSessions.add(sessionKey);
            payloadBuilder.recordUserContext(sessionKey, description);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("collector", configType);
            metadata = projectContextEnricher.enrichForTaskStart(sourceTool, sessionKey, metadata);
            eventBuffer.enqueueTaskStart(new TaskStartRequest(
                    taskId,
                    sessionKey,
                    sourceTool,
                    truncate(description, 500),
                    LogTimestampParser.resolve(node, sessionKey),
                    metadata
            ));
            eventBuffer.enqueueTaskEvent(taskId, TaskEventType.SESSION_START, false, Map.of());
        }

        long inputTokens = TokenEstimator.estimateMessage(node.path("message"));
        if (inputTokens > 0) {
            eventBuffer.enqueueTokenUsage(taskId, inputTokens, 0, 0, 0, 0,
                    node.path("model").asText(defaultModel));
        }
    }

    private void handleAssistantMessage(String sessionKey, JsonNode node) {
        UUID taskId = sessionTasks.get(sessionKey);
        if (taskId == null) {
            return;
        }

        JsonNode usage = node.path("usage");
        if (!usage.isMissingNode()) {
            eventBuffer.enqueueTokenUsage(taskId, usage, node.path("model").asText(defaultModel));
        }

        JsonNode content = node.path("message").path("content");
        if (content.isArray()) {
            for (JsonNode block : content) {
                if (!"tool_use".equals(block.path("type").asText())) {
                    continue;
                }
                String toolName = block.path("name").asText().toLowerCase();
                if (EDIT_EVENT_TYPES.contains(toolName)) {
                    handleFileEdit(sessionKey, taskId, block.path("input"));
                }
            }
        }
    }

    private void handleFileEdit(String sessionKey, UUID taskId, JsonNode node) {
        String filePath = payloadBuilder.resolveFilePath(node);
        int prior = fileEditCounts.computeIfAbsent(sessionKey + ":" + filePath, k -> new AtomicInteger(0))
                .getAndIncrement();
        boolean retry = retryDetector.isFileReEdit(prior);
        if (retry) {
            retryCounts.computeIfAbsent(sessionKey, k -> new AtomicInteger(0)).incrementAndGet();
        }
        Map<String, Object> gitBackfill = new HashMap<>();
        projectContextEnricher.backfillFromEdit(sourceTool, sessionKey, taskId, filePath, gitBackfill);
        Map<String, Object> payload = new HashMap<>(
                payloadBuilder.editEvent(sessionKey, "edit", filePath, prior, retry, node)
        );
        payload.putAll(gitBackfill);
        eventBuffer.enqueueTaskEvent(taskId, TaskEventType.EDIT, retry, payload);
    }

    private void handleTerminalCommand(String sessionKey, UUID taskId, JsonNode node) {
        int exitCode = node.path("exit_code").asInt(node.path("exitCode").asInt(0));
        String command = node.path("command").asText("");
        boolean failed = exitCode != 0;
        retryDetector.recordBashResult(sessionKey, failed);
        if (failed) {
            retryCounts.computeIfAbsent(sessionKey, k -> new AtomicInteger(0)).incrementAndGet();
            eventBuffer.enqueueTaskEvent(taskId, TaskEventType.TEST_FAIL, true,
                    payloadBuilder.testFailure(command, exitCode, null, sessionKey));
        }
    }

    private void completeSession(String sessionKey) {
        UUID taskId = sessionTasks.get(sessionKey);
        if (taskId == null) {
            return;
        }
        int retries = retryCounts.getOrDefault(sessionKey, new AtomicInteger(0)).get();
        eventBuffer.enqueueTaskComplete(new TaskCompleteRequest(
                taskId,
                TaskStatus.COMPLETED,
                retries,
                Instant.now()
        ));
        startedSessions.remove(sessionKey);
    }

    private String extractText(JsonNode node) {
        for (String field : List.of("text", "content", "message", "prompt")) {
            JsonNode value = node.get(field);
            if (value == null || value.isMissingNode()) {
                continue;
            }
            if (value.isTextual()) {
                return value.asText("").trim();
            }
            String nested = TokenEstimator.extractText(value);
            if (!nested.isBlank()) {
                return nested.trim();
            }
        }
        return TokenEstimator.extractText(node).trim();
    }
}
