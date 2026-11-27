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

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class CopilotCollector extends AbstractSessionCollector {

    private static final Set<String> EDIT_TOOLS = Set.of(
            "edit", "write", "apply_patch", "create_file", "replace_string_in_file"
    );

    private final EventBuffer eventBuffer;
    private final RetryDetector retryDetector;
    private final ObjectMapper objectMapper;
    private final EventPayloadBuilder payloadBuilder;
    private final Map<String, UUID> sessionTasks = new ConcurrentHashMap<>();
    private final Map<String, String> sessionModels = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> fileEditCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> retryCounts = new ConcurrentHashMap<>();
    private final Set<String> startedSessions = ConcurrentHashMap.newKeySet();

    public CopilotCollector(
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
        return SourceTool.COPILOT;
    }

    @Override
    public boolean isEnabled() {
        return isCollectorEnabled("copilot");
    }

    @Override
    public List<Path> watchPaths() {
        return resolveWatchPaths("copilot", "~/.copilot/session-state");
    }

    @Override
    public void scan() {
        for (Path root : watchPaths()) {
            fileWatcher.scanDirectory(root, "events.jsonl", this::parseLine);
        }
    }

    private void parseLine(String sessionKey, String line) {
        try {
            JsonNode node = objectMapper.readTree(line);
            String type = node.path("type").asText();
            JsonNode data = node.path("data");
            UUID taskId = sessionTasks.computeIfAbsent(sessionKey, key -> SessionTaskIds.forSession(SourceTool.COPILOT, key));

            switch (type) {
                case "session.start" -> handleSessionStart(sessionKey, taskId, node);
                case "user.message" -> handleUserMessage(sessionKey, taskId, node);
                case "tool.execution_start" -> handleToolStart(sessionKey, taskId, data);
                case "tool.execution_complete" -> handleToolComplete(sessionKey, taskId, data);
                case "session.compaction_complete" ->
                        eventBuffer.enqueueTaskEvent(taskId, TaskEventType.COMPACTION, false, Map.of());
                case "subagent.started" ->
                        eventBuffer.enqueueTaskEvent(taskId, TaskEventType.SUBAGENT, false, Map.of());
                case "session.shutdown", "session.task_complete" -> handleSessionEnd(sessionKey, taskId, data);
                default -> {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void handleSessionStart(String sessionKey, UUID taskId, JsonNode event) {
        if (!startedSessions.add(sessionKey)) {
            return;
        }
        JsonNode data = event.path("data");
        String model = data.path("model").asText(data.path("context").path("model").asText("copilot"));
        sessionModels.put(sessionKey, model);
        eventBuffer.enqueueTaskStart(new TaskStartRequest(
                taskId,
                sessionKey,
                SourceTool.COPILOT,
                truncate("Copilot session (" + model + ")", 500),
                LogTimestampParser.resolve(event, sessionKey),
                Map.of("collector", "copilot", "model", model)
        ));
        eventBuffer.enqueueTaskEvent(taskId, TaskEventType.SESSION_START, false, Map.of("model", model));
    }

    private void handleUserMessage(String sessionKey, UUID taskId, JsonNode event) {
        if (!startedSessions.contains(sessionKey)) {
            handleSessionStart(sessionKey, taskId, event);
        }
        JsonNode data = event.path("data");
        String prompt = data.path("content").asText(data.path("message").asText(""));
        if (!prompt.isBlank() && startedSessions.contains(sessionKey)) {
            payloadBuilder.recordUserContext(sessionKey, prompt);
            eventBuffer.enqueueTaskEvent(taskId, TaskEventType.SESSION_START, false,
                    Map.of("promptPreview", truncate(prompt, 120)));
        }
    }

    private void handleToolStart(String sessionKey, UUID taskId, JsonNode data) {
        String toolName = data.path("toolName").asText(data.path("name").asText("unknown")).toLowerCase();
        JsonNode args = data.path("arguments");
        if (EDIT_TOOLS.contains(toolName)) {
            JsonNode input = args.isMissingNode() ? data : args;
            String filePath = payloadBuilder.resolveFilePath(input);
            int prior = fileEditCounts.computeIfAbsent(sessionKey + ":" + filePath, k -> new AtomicInteger(0))
                    .getAndIncrement();
            boolean retry = retryDetector.isFileReEdit(prior);
            if (retry) {
                retryCounts.computeIfAbsent(sessionKey, k -> new AtomicInteger(0)).incrementAndGet();
            }
            eventBuffer.enqueueTaskEvent(taskId, TaskEventType.EDIT, retry,
                    payloadBuilder.editEvent(sessionKey, toolName, filePath, prior, retry, input));
        }
    }

    private void handleToolComplete(String sessionKey, UUID taskId, JsonNode data) {
        String toolName = data.path("toolName").asText(data.path("name").asText("")).toLowerCase();
        if (!"shell".equals(toolName) && !"bash".equals(toolName) && !"run_in_terminal".equals(toolName)) {
            return;
        }
        boolean failed = !data.path("success").asBoolean(true);
        int exitCode = data.path("exitCode").asInt(data.path("exit_code").asInt(failed ? 1 : 0));
        retryDetector.recordBashResult(sessionKey, failed);
        if (failed) {
            retryCounts.computeIfAbsent(sessionKey, k -> new AtomicInteger(0)).incrementAndGet();
            eventBuffer.enqueueTaskEvent(taskId, TaskEventType.TEST_FAIL, true,
                    payloadBuilder.testFailure(
                            data.path("command").asText(""),
                            exitCode,
                            data.path("output").asText(data.path("stderr").asText("")),
                            sessionKey
                    ));
        }
    }

    private void handleSessionEnd(String sessionKey, UUID taskId, JsonNode data) {
        JsonNode metrics = data.path("modelMetrics");
        if (!metrics.isMissingNode()) {
            metrics.fields().forEachRemaining(entry -> {
                JsonNode usage = entry.getValue().path("usage");
                if (!usage.isMissingNode()) {
                    eventBuffer.enqueueTokenUsage(taskId, usage, entry.getKey());
                }
            });
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
}
