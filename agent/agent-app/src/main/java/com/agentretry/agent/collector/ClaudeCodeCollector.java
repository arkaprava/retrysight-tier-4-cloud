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
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ClaudeCodeCollector extends AbstractSessionCollector {

    private final EventBuffer eventBuffer;
    private final RetryDetector retryDetector;
    private final ObjectMapper objectMapper;
    private final EventPayloadBuilder payloadBuilder;
    private final ProjectContextEnricher projectContextEnricher;
    private final ParseHealthTracker parseHealthTracker;
    private final IdleSessionTracker idleSessions = new IdleSessionTracker();
    private final Map<String, UUID> sessionTasks = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> fileEditCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> retryCounts = new ConcurrentHashMap<>();
    private final Set<String> startedSessions = ConcurrentHashMap.newKeySet();

    public ClaudeCodeCollector(
            AgentProperties properties,
            EventBuffer eventBuffer,
            RetryDetector retryDetector,
            ObjectMapper objectMapper,
            EventPayloadBuilder payloadBuilder,
            ProjectContextEnricher projectContextEnricher,
            ParseHealthTracker parseHealthTracker
    ) {
        super(properties);
        this.eventBuffer = eventBuffer;
        this.retryDetector = retryDetector;
        this.objectMapper = objectMapper;
        this.payloadBuilder = payloadBuilder;
        this.projectContextEnricher = projectContextEnricher;
        this.parseHealthTracker = parseHealthTracker;
    }

    @Override
    public SourceTool sourceTool() {
        return SourceTool.CLAUDE_CODE;
    }

    @Override
    public boolean isEnabled() {
        return isCollectorEnabled("claude_code");
    }

    @Override
    public List<Path> watchPaths() {
        return resolveWatchPaths("claude_code", "~/.claude/projects");
    }

    @Override
    public void scan() {
        for (Path root : watchPaths()) {
            fileWatcher.scanDirectory(root, ".jsonl", this::parseLine);
        }
    }

    @Override
    public void expireIdleSessions(Duration idleThreshold) {
        idleSessions.expireIdle(idleThreshold, sessionKey -> {
            completeSession(sessionKey);
            idleSessions.forget(sessionKey);
        });
    }

    private void parseLine(String sessionKey, String line) {
        try {
            idleSessions.touch(sessionKey);
            JsonNode node = objectMapper.readTree(line);
            if (!node.has("type")) {
                return;
            }

            String type = node.get("type").asText();
            switch (type) {
                case "user" -> handleUserMessage(sessionKey, node);
                case "assistant" -> handleAssistantMessage(sessionKey, node);
                case "system" -> handleSystemMessage(sessionKey, node);
                case "summary", "compact_boundary" -> handleCompaction(sessionKey);
                default -> {
                }
            }
        } catch (Exception ex) {
            parseHealthTracker.recordParseError(SourceTool.CLAUDE_CODE);
        }
    }

    private void handleUserMessage(String sessionKey, JsonNode node) {
        UUID taskId = sessionTasks.computeIfAbsent(sessionKey, key -> SessionTaskIds.forSession(SourceTool.CLAUDE_CODE, key));

        if (!startedSessions.contains(sessionKey)) {
            String description = extractText(node.path("message"));
            if (description.isBlank()) {
                return;
            }
            payloadBuilder.recordUserContext(sessionKey, description);
            startedSessions.add(sessionKey);
            boolean subagent = sessionKey.contains("/subagents/") || sessionKey.contains("/tasks/");
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("collector", "claude_code");
            metadata.put("subagent", subagent);
            metadata = projectContextEnricher.enrichForTaskStart(SourceTool.CLAUDE_CODE, sessionKey, metadata);

            eventBuffer.enqueueTaskStart(new TaskStartRequest(
                    taskId,
                    sessionKey,
                    SourceTool.CLAUDE_CODE,
                    truncate(description, 500),
                    LogTimestampParser.resolve(node, sessionKey),
                    metadata
            ));
            if (subagent) {
                eventBuffer.enqueueTaskEvent(taskId, TaskEventType.SUBAGENT, false, Map.of());
            } else {
                eventBuffer.enqueueTaskEvent(taskId, TaskEventType.SESSION_START, false, Map.of());
            }
        }
    }

    private void handleAssistantMessage(String sessionKey, JsonNode node) {
        UUID taskId = sessionTasks.get(sessionKey);
        if (taskId == null) {
            return;
        }

        if (node.has("usage")) {
            eventBuffer.enqueueTokenUsage(taskId, node.get("usage"), node.path("model").asText(null));
        }

        JsonNode content = node.path("message").path("content");
        if (!content.isArray()) {
            return;
        }

        for (JsonNode block : content) {
            if (!"tool_use".equals(block.path("type").asText())) {
                continue;
            }
            String toolName = block.path("name").asText();
            JsonNode input = block.path("input");

            if ("Edit".equals(toolName) || "Write".equals(toolName)) {
                String filePath = payloadBuilder.resolveFilePath(input);
                int prior = fileEditCounts.computeIfAbsent(sessionKey + ":" + filePath, k -> new AtomicInteger(0))
                        .getAndIncrement();
                boolean retry = retryDetector.isFileReEdit(prior);
                if (retry) {
                    retryCounts.computeIfAbsent(sessionKey, k -> new AtomicInteger(0)).incrementAndGet();
                }
                Map<String, Object> gitBackfill = new HashMap<>();
                projectContextEnricher.backfillFromEdit(
                        SourceTool.CLAUDE_CODE, sessionKey, taskId, filePath, gitBackfill
                );
                Map<String, Object> payload = new HashMap<>(
                        payloadBuilder.editEvent(sessionKey, toolName, filePath, prior, retry, input)
                );
                payload.putAll(gitBackfill);
                eventBuffer.enqueueTaskEvent(taskId, retry ? TaskEventType.EDIT : TaskEventType.DIFF_ACCEPTED, retry, payload);
            }

            if ("Bash".equals(toolName)) {
                String command = input.path("command").asText("");
                boolean looksLikeTest = command.contains("test") || command.contains("pytest")
                        || command.contains("mvn") || command.contains("gradle");
                if (looksLikeTest) {
                    eventBuffer.enqueueTaskEvent(taskId, TaskEventType.TEST_FAIL,
                            retryDetector.wasLastBashFailure(sessionKey),
                            payloadBuilder.testFailure(command, 1, null, sessionKey));
                }
            }
        }

        if (node.path("message").path("stop_reason").asText().equals("end_turn")) {
            eventBuffer.enqueueTaskEvent(taskId, TaskEventType.DIFF_ACCEPTED, false,
                    payloadBuilder.diffAccepted(sessionKey, 0, "end_turn", node.path("message"), List.of()));
        }
    }

    private void handleSystemMessage(String sessionKey, JsonNode node) {
        if ("task_complete".equals(node.path("subtype").asText())) {
            completeSession(sessionKey);
        }
    }

    private void handleCompaction(String sessionKey) {
        UUID taskId = sessionTasks.get(sessionKey);
        if (taskId != null) {
            eventBuffer.enqueueTaskEvent(taskId, TaskEventType.COMPACTION, false, Map.of());
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
        idleSessions.forget(sessionKey);
    }

    private String extractText(JsonNode message) {
        JsonNode content = message.path("content");
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray() && !content.isEmpty()) {
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText())) {
                    return block.path("text").asText("");
                }
            }
        }
        return "";
    }
}
