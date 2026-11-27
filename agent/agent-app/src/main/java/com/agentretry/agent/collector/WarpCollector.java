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
import com.agentretry.contract.TaskEventType;
import com.agentretry.contract.TaskStartRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class WarpCollector extends AbstractSessionCollector {

    private static final Logger log = LoggerFactory.getLogger(WarpCollector.class);

    private final EventBuffer eventBuffer;
    private final RetryDetector retryDetector;
    private final ObjectMapper objectMapper;
    private final Map<String, UUID> conversationTasks = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> retryCounts = new ConcurrentHashMap<>();
    private long lastQueryId;

    public WarpCollector(
            AgentProperties properties,
            EventBuffer eventBuffer,
            RetryDetector retryDetector,
            ObjectMapper objectMapper
    ) {
        super(properties);
        this.eventBuffer = eventBuffer;
        this.retryDetector = retryDetector;
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceTool sourceTool() {
        return SourceTool.WARP;
    }

    @Override
    public boolean isEnabled() {
        return isCollectorEnabled("warp");
    }

    @Override
    public List<Path> watchPaths() {
        return resolveWatchPaths("warp", WarpDatabasePaths.defaultDatabasePath().toString());
    }

    @Override
    public void scan() {
        Path databasePath = watchPaths().stream().findFirst().orElse(WarpDatabasePaths.defaultDatabasePath());
        if (!WarpDatabasePaths.exists(databasePath)) {
            return;
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath)) {
            scanQueries(conn);
            scanBlocks(conn);
            scanConversationUsage(conn);
        } catch (Exception ex) {
            log.trace("Warp collector scan skipped: {}", ex.getMessage());
        }
    }

    private void scanQueries(Connection conn) throws Exception {
        String sql = """
                SELECT id, conversation_id, input, model_id, start_ts
                FROM ai_queries
                WHERE id > ?
                ORDER BY id
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, lastQueryId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    lastQueryId = id;
                    String conversationId = rs.getString("conversation_id");
                    String inputJson = rs.getString("input");
                    String model = rs.getString("model_id");
                    long startTs = rs.getLong("start_ts");
                    handleQuery(conversationId, inputJson, model, startTs);
                }
            }
        }
    }

    private void handleQuery(String conversationId, String inputJson, String model, long startTs) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }

        UUID taskId = conversationTasks.computeIfAbsent(conversationId, key -> SessionTaskIds.forSession(SourceTool.WARP, key));
        String description = extractQueryText(inputJson);
        if (description.isBlank()) {
            description = "Warp agent session";
        }

        if (!hasStarted(conversationId)) {
            markStarted(conversationId);
            Instant startedAt = LogTimestampParser.resolveFromWarpStartTs(startTs);
            if (startedAt == null) {
                startedAt = Instant.now();
            }
            eventBuffer.enqueueTaskStart(new TaskStartRequest(
                    taskId,
                    conversationId,
                    SourceTool.WARP,
                    truncate(description, 500),
                    startedAt,
                    Map.of("collector", "warp", "model", model != null ? model : "warp")
            ));
            eventBuffer.enqueueTaskEvent(taskId, TaskEventType.SESSION_START, false, Map.of());
        }
    }

    private void scanBlocks(Connection conn) throws Exception {
        String sql = """
                SELECT ai_metadata, exit_code
                FROM blocks
                WHERE ai_metadata IS NOT NULL
                  AND ai_metadata != ''
                ORDER BY id DESC
                LIMIT 200
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String metadata = rs.getString("ai_metadata");
                int exitCode = rs.getInt("exit_code");
                String conversationId = extractConversationId(metadata);
                if (conversationId == null) {
                    continue;
                }
                UUID taskId = conversationTasks.get(conversationId);
                if (taskId == null) {
                    continue;
                }

                if (exitCode != 0) {
                    retryCounts.computeIfAbsent(conversationId, k -> new AtomicInteger(0)).incrementAndGet();
                    retryDetector.recordBashResult(conversationId, true);
                    eventBuffer.enqueueTaskEvent(taskId, TaskEventType.TEST_FAIL, true,
                            Map.of("exitCode", exitCode));
                } else {
                    retryDetector.recordBashResult(conversationId, false);
                }
            }
        }
    }

    private void scanConversationUsage(Connection conn) throws Exception {
        String sql = """
                SELECT conversation_id, conversation_data
                FROM agent_conversations
                WHERE conversation_data IS NOT NULL
                  AND conversation_data != ''
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String conversationId = rs.getString("conversation_id");
                UUID taskId = conversationTasks.get(conversationId);
                if (taskId == null) {
                    continue;
                }
                parseUsage(taskId, rs.getString("conversation_data"));
            }
        }
    }

    private void parseUsage(UUID taskId, String conversationData) {
        try {
            JsonNode root = objectMapper.readTree(conversationData);
            JsonNode tokenUsage = root.path("token_usage");
            if (tokenUsage.isArray()) {
                for (JsonNode entry : tokenUsage) {
                    long input = entry.path("input_tokens").asLong(entry.path("inputTokens").asLong(0));
                    long output = entry.path("output_tokens").asLong(entry.path("outputTokens").asLong(0));
                    if (input > 0 || output > 0) {
                        eventBuffer.enqueueTokenUsage(taskId, input, output, 0, 0, 0,
                                entry.path("model").asText("warp"));
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String extractQueryText(String inputJson) {
        if (inputJson == null || inputJson.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(inputJson);
            for (String field : List.of("text", "query", "prompt", "input")) {
                JsonNode value = node.get(field);
                if (value != null && value.isTextual()) {
                    return value.asText("").trim();
                }
            }
            if (node.isTextual()) {
                return node.asText("").trim();
            }
        } catch (Exception ignored) {
            return inputJson.trim();
        }
        return "";
    }

    private String extractConversationId(String metadata) {
        try {
            JsonNode node = objectMapper.readTree(metadata);
            String conversationId = node.path("conversation_id").asText(null);
            if (conversationId != null && !conversationId.isBlank()) {
                return conversationId;
            }
        } catch (Exception ignored) {
        }
        int marker = metadata.indexOf("conversation_id");
        if (marker >= 0) {
            int start = metadata.indexOf('"', marker + 15);
            int end = metadata.indexOf('"', start + 1);
            if (start >= 0 && end > start) {
                return metadata.substring(start + 1, end);
            }
        }
        return null;
    }

    private final Map<String, Boolean> startedConversations = new ConcurrentHashMap<>();

    private boolean hasStarted(String conversationId) {
        return startedConversations.getOrDefault(conversationId, false);
    }

    private void markStarted(String conversationId) {
        startedConversations.put(conversationId, true);
    }
}
