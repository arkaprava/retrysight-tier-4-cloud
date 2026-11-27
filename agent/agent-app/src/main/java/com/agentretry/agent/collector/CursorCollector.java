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
import com.fasterxml.jackson.databind.node.MissingNode;
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
public class CursorCollector extends AbstractSessionCollector {

    private static final Set<String> EDIT_TOOLS = Set.of(
            "edit", "strreplace", "write", "search_replace", "editnotebook", "apply_patch"
    );

    private final EventBuffer eventBuffer;
    private final RetryDetector retryDetector;
    private final ObjectMapper objectMapper;
    private final EventPayloadBuilder payloadBuilder;
    private final ProjectContextEnricher projectContextEnricher;
    private final CursorModelResolver cursorModelResolver;
    private final ParseHealthTracker parseHealthTracker;
    private final IdleSessionTracker idleSessions = new IdleSessionTracker();
    private final Map<String, UUID> sessionTasks = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> fileEditCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> turnCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> retryCounts = new ConcurrentHashMap<>();
    private final Map<String, List<String>> pendingTurnFiles = new ConcurrentHashMap<>();
    private final Set<String> startedSessions = ConcurrentHashMap.newKeySet();
    private final Set<String> modelsAttributed = ConcurrentHashMap.newKeySet();

    public CursorCollector(
            AgentProperties properties,
            EventBuffer eventBuffer,
            RetryDetector retryDetector,
            ObjectMapper objectMapper,
            EventPayloadBuilder payloadBuilder,
            ProjectContextEnricher projectContextEnricher,
            CursorModelResolver cursorModelResolver,
            ParseHealthTracker parseHealthTracker
    ) {
        super(properties);
        this.eventBuffer = eventBuffer;
        this.retryDetector = retryDetector;
        this.objectMapper = objectMapper;
        this.payloadBuilder = payloadBuilder;
        this.projectContextEnricher = projectContextEnricher;
        this.cursorModelResolver = cursorModelResolver;
        this.parseHealthTracker = parseHealthTracker;
    }

    @Override
    public SourceTool sourceTool() {
        return SourceTool.CURSOR;
    }

    @Override
    public boolean isEnabled() {
        return isCollectorEnabled("cursor");
    }

    @Override
    public List<Path> watchPaths() {
        return resolveWatchPaths("cursor", "~/.cursor/projects");
    }

    @Override
    public void scan() {
        for (Path root : watchPaths()) {
            fileWatcher.scanDirectory(root, ".jsonl", this::parseLine);
        }
    }

    @Override
    public void expireIdleSessions(Duration idleThreshold) {
        idleSessions.expireIdle(idleThreshold, this::completeSession);
    }

    private void parseLine(String sessionKey, String line) {
        try {
            idleSessions.touch(sessionKey);
            JsonNode node = objectMapper.readTree(line);

            if (node.has("type") && "turn_ended".equals(node.get("type").asText())) {
                handleTurnEnded(sessionKey, node);
                return;
            }

            String role = node.path("role").asText();
            if ("user".equals(role)) {
                handleUserMessage(sessionKey, node);
            } else if ("assistant".equals(role)) {
                handleAssistantMessage(sessionKey, node);
            }
        } catch (Exception ex) {
            parseHealthTracker.recordParseError(SourceTool.CURSOR);
        }
    }

    private void handleUserMessage(String sessionKey, JsonNode node) {
        UUID taskId = sessionTasks.computeIfAbsent(sessionKey, key -> SessionTaskIds.forSession(SourceTool.CURSOR, key));
        JsonNode message = node.path("message");
        String rawText = TokenEstimator.extractText(message);

        if (!startedSessions.contains(sessionKey)) {
            String description = extractUserQuery(rawText);
            if (description.isBlank()) {
                description = truncate(rawText, 500);
            }
            if (description.isBlank()) {
                return;
            }

            payloadBuilder.recordUserContext(sessionKey, description);

            startedSessions.add(sessionKey);
            boolean subagent = sessionKey.contains("/subagents/");
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("collector", "cursor");
            metadata.put("subagent", subagent);
            metadata.put("conversationId", extractConversationId(sessionKey));
            metadata = projectContextEnricher.enrichForTaskStart(SourceTool.CURSOR, sessionKey, metadata);
            eventBuffer.enqueueTaskStart(new TaskStartRequest(
                    taskId,
                    sessionKey,
                    SourceTool.CURSOR,
                    truncate(description, 500),
                    LogTimestampParser.resolve(node, sessionKey),
                    metadata
            ));
            if (subagent) {
                eventBuffer.enqueueTaskEvent(taskId, TaskEventType.SUBAGENT, false,
                        Map.of("sessionKey", sessionKey));
            } else {
                eventBuffer.enqueueTaskEvent(taskId, TaskEventType.SESSION_START, false, Map.of());
            }
        }

        attributeModels(sessionKey, taskId);

        long inputTokens = TokenEstimator.estimateMessage(message);
        if (inputTokens > 0) {
            Instant at = LogTimestampParser.resolve(node, sessionKey);
            eventBuffer.enqueueTokenUsage(taskId, inputTokens, 0, 0, 0, 0,
                    resolveModel(sessionKey, node, at));
        }
    }

    private void handleAssistantMessage(String sessionKey, JsonNode node) {
        UUID taskId = sessionTasks.get(sessionKey);
        if (taskId == null) {
            return;
        }

        attributeModels(sessionKey, taskId);
        recordTokenUsage(sessionKey, taskId, node);

        JsonNode content = node.path("message").path("content");
        if (!content.isArray()) {
            return;
        }

        for (JsonNode block : content) {
            if (!"tool_use".equals(block.path("type").asText())) {
                continue;
            }
            String toolName = block.path("name").asText().toLowerCase();
            JsonNode input = block.path("input");

            if (EDIT_TOOLS.contains(toolName)) {
                String filePath = payloadBuilder.resolveFilePath(input);
                int prior = fileEditCounts.computeIfAbsent(sessionKey + ":" + filePath, k -> new AtomicInteger(0))
                        .getAndIncrement();
                boolean retry = retryDetector.isFileReEdit(prior);
                if (retry) {
                    retryCounts.computeIfAbsent(sessionKey, k -> new AtomicInteger(0)).incrementAndGet();
                }
                trackPendingTurnFile(sessionKey, filePath);
                Map<String, Object> gitBackfill = new HashMap<>();
                projectContextEnricher.backfillFromEdit(
                        SourceTool.CURSOR, sessionKey, taskId, filePath, gitBackfill
                );
                Map<String, Object> payload = new HashMap<>(
                        payloadBuilder.editEvent(sessionKey, toolName, filePath, prior, retry, input)
                );
                payload.putAll(gitBackfill);
                eventBuffer.enqueueTaskEvent(taskId, TaskEventType.EDIT, retry, payload);
            }

            if ("shell".equals(toolName)) {
                String command = input.path("command").asText("");
                boolean looksLikeTest = command.contains("test") || command.contains("pytest") || command.contains("mvn");
                if (looksLikeTest && retryDetector.wasLastBashFailure(sessionKey)) {
                    retryCounts.computeIfAbsent(sessionKey, k -> new AtomicInteger(0)).incrementAndGet();
                    eventBuffer.enqueueTaskEvent(taskId, TaskEventType.TEST_FAIL, true,
                            payloadBuilder.testFailure(command, 1, null, sessionKey));
                }
            }
        }
    }

    private void handleTurnEnded(String sessionKey, JsonNode node) {
        UUID taskId = sessionTasks.get(sessionKey);
        if (taskId == null) {
            return;
        }

        attributeModels(sessionKey, taskId);
        recordTokenUsage(sessionKey, taskId, node);

        int turn = turnCounts.computeIfAbsent(sessionKey, k -> new AtomicInteger(0)).incrementAndGet();
        String status = node.path("status").asText("unknown");
        List<String> affectedFiles = EventPayloadBuilder.distinctFiles(
                pendingTurnFiles.getOrDefault(sessionKey, List.of())
        );
        pendingTurnFiles.remove(sessionKey);

        if ("success".equals(status)) {
            eventBuffer.enqueueTaskEvent(taskId, TaskEventType.DIFF_ACCEPTED, false,
                    payloadBuilder.diffAccepted(sessionKey, turn, status, node, affectedFiles));
        } else {
            retryCounts.computeIfAbsent(sessionKey, k -> new AtomicInteger(0)).incrementAndGet();
            eventBuffer.enqueueTaskEvent(taskId, TaskEventType.DIFF_REJECTED, true,
                    payloadBuilder.diffRejected(sessionKey, turn, status, node, affectedFiles));
        }
    }

    public void completeSession(String sessionKey) {
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
        modelsAttributed.remove(sessionKey);
        idleSessions.forget(sessionKey);
        cleanupSessionState(sessionKey);
    }

    private void cleanupSessionState(String sessionKey) {
        sessionTasks.remove(sessionKey);
        fileEditCounts.keySet().removeIf(key -> key.startsWith(sessionKey + ":"));
        turnCounts.remove(sessionKey);
        retryCounts.remove(sessionKey);
        pendingTurnFiles.remove(sessionKey);
    }

    private void recordTokenUsage(String sessionKey, UUID taskId, JsonNode node) {
        Instant at = LogTimestampParser.resolve(node, sessionKey);
        String model = resolveModel(sessionKey, node, at);
        JsonNode usage = firstUsageNode(node);
        if (!usage.isMissingNode() && !usage.isNull()) {
            eventBuffer.enqueueTokenUsage(taskId, usage, model);
            return;
        }

        JsonNode message = node.path("message");
        if (message.isMissingNode()) {
            return;
        }

        long outputTokens = TokenEstimator.estimateMessage(message);
        if (outputTokens > 0) {
            eventBuffer.enqueueTokenUsage(taskId, 0, outputTokens, 0, 0, 0, model);
        }
    }

    private String resolveModel(String sessionKey, JsonNode node, Instant at) {
        String transcriptModel = null;
        if (node.hasNonNull("model")) {
            transcriptModel = node.get("model").asText(null);
        }
        return cursorModelResolver.resolveModel(extractConversationId(sessionKey), at, transcriptModel);
    }

    private void attributeModels(String sessionKey, UUID taskId) {
        if (!modelsAttributed.add(sessionKey)) {
            return;
        }
        for (String model : cursorModelResolver.distinctModels(extractConversationId(sessionKey))) {
            eventBuffer.enqueueModelAttribution(taskId, model);
        }
    }

    private JsonNode firstUsageNode(JsonNode node) {
        for (String path : List.of("usage", "token_usage", "tokenUsage")) {
            JsonNode usage = node.path(path);
            if (!usage.isMissingNode() && !usage.isNull()) {
                return usage;
            }
        }
        JsonNode nested = node.path("message").path("usage");
        if (!nested.isMissingNode() && !nested.isNull()) {
            return nested;
        }
        return MissingNode.getInstance();
    }

    private String extractMessageText(JsonNode message) {
        return TokenEstimator.extractText(message);
    }

    private void trackPendingTurnFile(String sessionKey, String filePath) {
        pendingTurnFiles.computeIfAbsent(sessionKey, key -> new java.util.ArrayList<>());
        if (!pendingTurnFiles.get(sessionKey).contains(filePath)) {
            pendingTurnFiles.get(sessionKey).add(filePath);
        }
    }

    private String extractConversationId(String sessionKey) {
        Path path = Path.of(sessionKey);
        String fileName = path.getFileName().toString();
        if (fileName.endsWith(".jsonl")) {
            return fileName.substring(0, fileName.length() - 6);
        }
        return fileName;
    }
}
