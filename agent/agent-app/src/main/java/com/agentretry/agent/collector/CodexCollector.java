/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.collector;

import com.agentretry.agent.config.AgentProperties;
import com.agentretry.agent.detector.RetryDetector;
import com.agentretry.agent.uploader.EventBuffer;
import com.agentretry.contract.SessionTaskIds;
import com.agentretry.contract.SourceTool;
import com.agentretry.contract.TaskCompleteRequest;
import com.agentretry.contract.TaskEventType;
import com.agentretry.contract.TaskStartRequest;
import com.agentretry.contract.TaskStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class CodexCollector extends AbstractSessionCollector {

    private final EventBuffer eventBuffer;
    private final RetryDetector retryDetector;
    private final ObjectMapper objectMapper;
    private final EventPayloadBuilder payloadBuilder;
    private final Map<String, UUID> sessionTasks = new ConcurrentHashMap<>();
    private final Map<String, String> sessionModels = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> fileEditCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> tokenTurns = new ConcurrentHashMap<>();
    private final Set<String> startedSessions = ConcurrentHashMap.newKeySet();
    private final IdleSessionTracker idleSessions = new IdleSessionTracker();

    public CodexCollector(
            AgentProperties properties,
            EventBuffer eventBuffer,
            RetryDetector retryDetector,
            ObjectMapper objectMapper,
            EventPayloadBuilder payloadBuilder
    ) {
        super(properties);
        this.eventBuffer = eventBuffer;
        this.retryDetector = retryDetector;
        this.objectMapper = objectMapper;
        this.payloadBuilder = payloadBuilder;
    }

    @Override
    public SourceTool sourceTool() {
        return SourceTool.CODEX;
    }

    @Override
    public boolean isEnabled() {
        return isCollectorEnabled("codex");
    }

    @Override
    public List<java.nio.file.Path> watchPaths() {
        return resolveWatchPaths("codex", "~/.codex/sessions");
    }

    @Override
    public void scan() {
        for (java.nio.file.Path root : watchPaths()) {
            fileWatcher.scanDirectory(root, ".jsonl", this::parseLine);
        }
    }

    @Override
    public void expireIdleSessions(Duration idleThreshold) {
        idleSessions.expireIdle(idleThreshold, sessionKey -> {
            if (startedSessions.contains(sessionKey)) {
                completeSession(sessionKey);
            }
            idleSessions.forget(sessionKey);
        });
    }

    private void parseLine(String sessionKey, String line) {
        try {
            idleSessions.touch(sessionKey);
            JsonNode node = objectMapper.readTree(line);
            String type = node.path("type").asText();

            if ("event_msg".equals(type)) {
                handleEventMsg(sessionKey, node);
                return;
            }

            if (node.has("payload")) {
                JsonNode payload = node.get("payload");
                if (payload.has("type")) {
                    handleEventMsg(sessionKey, node);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void handleEventMsg(String sessionKey, JsonNode record) {
        JsonNode payload = record.path("payload");
        String eventType = payload.path("type").asText();
        UUID taskId = sessionTasks.computeIfAbsent(sessionKey, key -> SessionTaskIds.forSession(SourceTool.CODEX, key));

        switch (eventType) {
            case "task_started" -> handleTaskStarted(sessionKey, taskId, record, payload);
            case "token_count", "token_usage" -> handleTokenUsage(sessionKey, taskId, payload);
            case "tool_call" -> handleToolCall(sessionKey, taskId, payload);
            case "task_completed" -> handleTaskCompleted(sessionKey, taskId, payload);
            default -> {
            }
        }
    }

    private void handleTaskStarted(String sessionKey, UUID taskId, JsonNode record, JsonNode payload) {
        if (!startedSessions.add(sessionKey)) {
            return;
        }
        String model = payload.path("model").asText("codex");
        sessionModels.put(sessionKey, model);

        eventBuffer.enqueueTaskStart(new TaskStartRequest(
                taskId,
                sessionKey,
                SourceTool.CODEX,
                truncate("Codex task (" + model + ")", 500),
                LogTimestampParser.resolve(record, sessionKey),
                Map.of(
                        "collector", "codex",
                        "model", model,
                        "contextWindow", payload.path("model_context_window").asInt(0)
                )
        ));
        eventBuffer.enqueueTaskEvent(taskId, TaskEventType.SESSION_START, false,
                Map.of("model", model));
    }

    private void handleTokenUsage(String sessionKey, UUID taskId, JsonNode payload) {
        JsonNode info = payload.path("info");
        if (info.isMissingNode()) {
            info = payload;
        }
        int turn = tokenTurns.computeIfAbsent(sessionKey, k -> new AtomicInteger(0)).incrementAndGet();
        eventBuffer.enqueueCodexTokenUsage(taskId, info, sessionModels.get(sessionKey), turn);
        eventBuffer.enqueueTaskEvent(taskId, TaskEventType.TOKEN_USAGE, false,
                Map.of("turn", turn));
    }

    private void handleToolCall(String sessionKey, UUID taskId, JsonNode payload) {
        String toolName = payload.path("tool").asText(payload.path("name").asText("unknown"));
        String filePath = extractFilePath(payload);

        if (isEditTool(toolName) && filePath != null) {
            JsonNode input = payload.path("arguments").isMissingNode() ? payload : payload.path("arguments");
            int prior = fileEditCounts.computeIfAbsent(sessionKey + ":" + filePath, k -> new AtomicInteger(0))
                    .getAndIncrement();
            boolean retry = retryDetector.isFileReEdit(prior);
            eventBuffer.enqueueTaskEvent(taskId, TaskEventType.EDIT, retry,
                    payloadBuilder.editEvent(sessionKey, toolName, filePath, prior, retry, input));
        }

        if ("shell".equalsIgnoreCase(toolName) || "bash".equalsIgnoreCase(toolName)) {
            int exitCode = payload.path("exit_code").asInt(payload.path("exitCode").asInt(0));
            boolean failed = exitCode != 0;
            if (failed) {
                eventBuffer.enqueueTaskEvent(taskId, TaskEventType.TEST_FAIL, true,
                        payloadBuilder.testFailure(
                                payload.path("command").asText(""),
                                exitCode,
                                payload.path("output").asText(""),
                                sessionKey
                        ));
            }
            retryDetector.recordBashResult(sessionKey, failed);
        }
    }

    private void handleTaskCompleted(String sessionKey, UUID taskId, JsonNode payload) {
        completeSession(sessionKey);
    }

    private void completeSession(String sessionKey) {
        UUID taskId = sessionTasks.get(sessionKey);
        if (taskId == null || !startedSessions.contains(sessionKey)) {
            return;
        }
        int retries = fileEditCounts.entrySet().stream()
                .filter(e -> e.getKey().startsWith(sessionKey + ":"))
                .mapToInt(e -> Math.max(0, e.getValue().get() - 1))
                .sum();

        eventBuffer.enqueueTaskComplete(new TaskCompleteRequest(
                taskId,
                TaskStatus.COMPLETED,
                retries,
                Instant.now()
        ));
        startedSessions.remove(sessionKey);
        sessionModels.remove(sessionKey);
        tokenTurns.remove(sessionKey);
        fileEditCounts.keySet().removeIf(key -> key.startsWith(sessionKey + ":"));
        idleSessions.forget(sessionKey);
    }

    private boolean isEditTool(String toolName) {
        return "edit".equalsIgnoreCase(toolName)
                || "write".equalsIgnoreCase(toolName)
                || "apply_patch".equalsIgnoreCase(toolName);
    }

    private String extractFilePath(JsonNode payload) {
        if (payload.has("file_path")) {
            return payload.get("file_path").asText();
        }
        if (payload.has("path")) {
            return payload.get("path").asText();
        }
        JsonNode args = payload.path("arguments");
        if (args.has("file_path")) {
            return args.get("file_path").asText();
        }
        if (args.has("path")) {
            return args.get("path").asText();
        }
        return null;
    }
}
