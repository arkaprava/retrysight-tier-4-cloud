/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.uploader;

import com.agentretry.agent.config.AgentProperties;
import com.agentretry.contract.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class EventBuffer {

    private static final Logger log = LoggerFactory.getLogger(EventBuffer.class);

    private final String dbPath;
    private final ObjectMapper objectMapper;
    private final int bufferMaxRows;
    private final int bufferMaxAgeHours;
    private final long bufferMaxBytes;
    private final AtomicLong droppedTotal = new AtomicLong();
    /** Per-task counters so multi-instance collectors emit aligned idempotency keys. */
    private final Map<UUID, Integer> sequenceByTask = new HashMap<>();

    public EventBuffer(AgentProperties properties, ObjectMapper objectMapper) {
        String configured = properties.getUpload().getBufferPath();
        this.dbPath = configured.replace("${user.home}", System.getProperty("user.home"));
        this.objectMapper = objectMapper;
        this.bufferMaxRows = properties.getUpload().getBufferMaxRows();
        this.bufferMaxAgeHours = properties.getUpload().getBufferMaxAgeHours();
        this.bufferMaxBytes = properties.getUpload().getBufferMaxBytes();
        initDatabase();
    }

    /** Test / ops visibility: cumulative rows dropped by cap enforcement. */
    public long droppedTotal() {
        return droppedTotal.get();
    }

    public synchronized void enqueueTaskStart(TaskStartRequest request) {
        insert(IngestItemType.TASK_START, request.taskId() + ":start", request);
    }

    public synchronized void updateTaskStartMetadata(UUID taskId, Map<String, Object> metadata) {
        TaskStartRequest updated = new TaskStartRequest(
                taskId,
                null,
                null,
                null,
                null,
                metadata
        );
        try (Connection conn = connection();
             PreparedStatement select = conn.prepareStatement(
                     "SELECT payload FROM pending_events WHERE idempotency_key = ?")) {
            select.setString(1, taskId + ":start");
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return;
                }
                TaskStartRequest existing = objectMapper.readValue(rs.getString("payload"), TaskStartRequest.class);
                Map<String, Object> merged = new java.util.HashMap<>();
                if (existing.metadata() != null) {
                    merged.putAll(existing.metadata());
                }
                merged.putAll(metadata);
                updated = new TaskStartRequest(
                        existing.taskId(),
                        existing.externalSessionId(),
                        existing.sourceTool(),
                        existing.description(),
                        existing.startedAt(),
                        merged
                );
            }
        } catch (Exception ex) {
            return;
        }

        try (Connection conn = connection();
             PreparedStatement update = conn.prepareStatement(
                     "UPDATE pending_events SET payload = ? WHERE idempotency_key = ?")) {
            update.setString(1, objectMapper.writeValueAsString(updated));
            update.setString(2, taskId + ":start");
            update.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    public synchronized void enqueueTaskComplete(TaskCompleteRequest request) {
        insert(IngestItemType.TASK_COMPLETE, request.taskId() + ":complete", request);
    }

    public synchronized void enqueueTaskEvent(UUID taskId, TaskEventType type, boolean retry, Map<String, Object> payload) {
        int sequence = nextSequence(taskId);
        TaskEventRequest event = new TaskEventRequest(
                taskId,
                type,
                Instant.now(),
                retry,
                sequence,
                payload
        );
        insert(IngestItemType.TASK_EVENT, taskId + ":event:" + sequence, event);
    }

    public synchronized void enqueueTokenUsage(UUID taskId, JsonNode usage, String model) {
        enqueueTokenUsage(
                taskId,
                firstLong(usage, "input_tokens", "inputTokens"),
                firstLong(usage, "output_tokens", "outputTokens"),
                firstLong(usage, "cache_read_input_tokens", "cache_read_tokens", "cacheReadTokens", "cached_input_tokens"),
                firstLong(usage, "cache_creation_input_tokens", "cache_write_tokens", "cacheWriteTokens"),
                firstLong(usage, "reasoning_tokens", "reasoning_output_tokens", "reasoningTokens"),
                model
        );
    }

    public synchronized void enqueueTokenUsage(
            UUID taskId,
            long inputTokens,
            long outputTokens,
            long cacheReadTokens,
            long cacheWriteTokens,
            long reasoningTokens,
            String model
    ) {
        if (inputTokens == 0 && outputTokens == 0 && cacheReadTokens == 0
                && cacheWriteTokens == 0 && reasoningTokens == 0) {
            return;
        }

        int turn = nextSequence(taskId);
        TokenUsageRequest request = new TokenUsageRequest(
                taskId,
                turn,
                model,
                inputTokens,
                outputTokens,
                cacheReadTokens,
                cacheWriteTokens,
                reasoningTokens,
                null,
                Instant.now()
        );
        insert(IngestItemType.TOKEN_USAGE, taskId + ":token:" + turn, request);
    }

    private int nextSequence(UUID taskId) {
        int next = sequenceByTask.getOrDefault(taskId, 0) + 1;
        sequenceByTask.put(taskId, next);
        return next;
    }

    /**
     * Records that a model participated in a task without adding token volume.
     * Used when Cursor transcripts omit model names but bubble state has them.
     */
    public synchronized void enqueueModelAttribution(UUID taskId, String model) {
        if (model == null || model.isBlank()) {
            return;
        }
        String normalized = model.trim();
        TokenUsageRequest request = new TokenUsageRequest(
                taskId,
                0,
                normalized,
                0,
                0,
                0,
                0,
                0,
                null,
                Instant.now()
        );
        insert(IngestItemType.TOKEN_USAGE, taskId + ":model:" + normalized, request);
    }

    public synchronized void enqueueCodexTokenUsage(UUID taskId, JsonNode info, String model, int turnIndex) {
        JsonNode usage = info.path("total_token_usage");
        if (usage.isMissingNode()) {
            usage = info;
        }
        TokenUsageRequest request = new TokenUsageRequest(
                taskId,
                turnIndex,
                model,
                usage.path("input_tokens").asLong(),
                usage.path("output_tokens").asLong(),
                usage.path("cached_input_tokens").asLong(),
                0,
                usage.path("reasoning_output_tokens").asLong(usage.path("reasoning_tokens").asLong()),
                null,
                Instant.now()
        );
        insert(IngestItemType.TOKEN_USAGE, taskId + ":token:" + turnIndex, request);
    }

    /**
     * Peeks a priority-ordered batch without deleting. Call {@link #acknowledge(List)}
     * only after the manager accepts the upload so failed sends can be retried.
     */
    public synchronized DrainBatch drain(int maxItems) {
        List<IngestBatchItem> items = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement(
                     """
                     SELECT id, idempotency_key, item_type, payload
                     FROM pending_events
                     ORDER BY
                       CASE
                         WHEN item_type = 'TASK_START' THEN 0
                         WHEN idempotency_key LIKE '%:model:%' THEN 1
                         ELSE 2
                       END,
                       id
                     LIMIT ?
                     """)) {
            ps.setInt(1, maxItems);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    String key = rs.getString("idempotency_key");
                    IngestItemType type = IngestItemType.valueOf(rs.getString("item_type"));
                    String payloadJson = rs.getString("payload");
                    Object payload = objectMapper.readValue(payloadJson, Object.class);
                    items.add(new IngestBatchItem(key, type, payload));
                    ids.add(id);
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to drain event buffer", ex);
        }
        return new DrainBatch(items, ids);
    }

    public synchronized void acknowledge(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        try (Connection conn = connection();
             PreparedStatement delete = conn.prepareStatement(
                     "DELETE FROM pending_events WHERE id = ?")) {
            for (Long id : ids) {
                delete.setLong(1, id);
                delete.addBatch();
            }
            delete.executeBatch();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to acknowledge uploaded events", ex);
        }
    }

    public synchronized int queueDepth() {
        try (Connection conn = connection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM pending_events")) {
            rs.next();
            return rs.getInt(1);
        } catch (Exception ex) {
            return 0;
        }
    }

    private long firstLong(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field)) {
                return node.get(field).asLong();
            }
        }
        return 0;
    }

    private void insert(IngestItemType type, String idempotencyKey, Object payload) {
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR IGNORE INTO pending_events (idempotency_key, item_type, payload, created_at) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, idempotencyKey);
            ps.setString(2, type.name());
            ps.setString(3, objectMapper.writeValueAsString(payload));
            ps.setString(4, Instant.now().toString());
            ps.executeUpdate();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to enqueue event", ex);
        }
        enforceCaps();
    }

    /**
     * Drops oldest buffered events when row count, age, or on-disk size exceeds configured limits.
     */
    synchronized void enforceCaps() {
        int dropped = 0;
        try (Connection conn = connection()) {
            if (bufferMaxAgeHours > 0) {
                Instant cutoff = Instant.now().minus(bufferMaxAgeHours, ChronoUnit.HOURS);
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM pending_events WHERE created_at < ?")) {
                    ps.setString(1, cutoff.toString());
                    dropped += Math.max(0, ps.executeUpdate());
                }
            }

            if (bufferMaxRows > 0) {
                int depth = countRows(conn);
                int overflow = depth - bufferMaxRows;
                if (overflow > 0) {
                    dropped += deleteOldest(conn, overflow);
                }
            }

            if (bufferMaxBytes > 0) {
                long fileBytes = java.nio.file.Files.size(java.nio.file.Path.of(dbPath));
                int guard = 0;
                while (fileBytes > bufferMaxBytes && guard++ < 32) {
                    int batch = Math.max(50, (int) Math.min(1000, Math.max(1, countRows(conn) / 10)));
                    int removed = deleteOldest(conn, batch);
                    if (removed == 0) {
                        break;
                    }
                    dropped += removed;
                    try (Statement st = conn.createStatement()) {
                        st.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                    } catch (Exception ignored) {
                        // Best-effort shrink; continue with size re-check.
                    }
                    fileBytes = java.nio.file.Files.size(java.nio.file.Path.of(dbPath));
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to enforce event buffer caps: {}", ex.getMessage());
            return;
        }

        if (dropped > 0) {
            long total = droppedTotal.addAndGet(dropped);
            log.warn("Event buffer over limit — dropped {} oldest row(s) (total dropped={})", dropped, total);
        }
    }

    private static int countRows(Connection conn) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM pending_events")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static int deleteOldest(Connection conn, int limit) throws Exception {
        if (limit <= 0) {
            return 0;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                """
                DELETE FROM pending_events
                WHERE id IN (
                  SELECT id FROM pending_events ORDER BY id ASC LIMIT ?
                )
                """)) {
            ps.setInt(1, limit);
            return Math.max(0, ps.executeUpdate());
        }
    }

    private void initDatabase() {
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of(dbPath).getParent());
        } catch (Exception ignored) {
        }
        try (Connection conn = connection(); Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA busy_timeout=5000");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS pending_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        idempotency_key TEXT NOT NULL UNIQUE,
                        item_type TEXT NOT NULL,
                        payload TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize SQLite buffer", ex);
        }
    }

    private Connection connection() throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA busy_timeout=5000");
        }
        return conn;
    }

    public record DrainBatch(List<IngestBatchItem> items, List<Long> ids) {
        public boolean isEmpty() {
            return items == null || items.isEmpty();
        }
    }
}
