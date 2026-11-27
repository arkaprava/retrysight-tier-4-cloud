/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads Cursor's local state DB for per-turn {@code modelInfo.modelName} values.
 * Agent transcript JSONL files usually omit the model field.
 */
@Component
public class CursorModelResolver {

    private static final Logger log = LoggerFactory.getLogger(CursorModelResolver.class);
    /** Avoid thrashing the ~1GB Cursor state DB while bubbles are still being written. */
    private static final long MIN_RELOAD_INTERVAL_MS = 2_000L;
    private static final Set<String> PLACEHOLDER_MODELS = Set.of("cursor", "unknown", "default");

    private final ObjectMapper objectMapper;
    private final Path stateDbPath;
    private volatile Snapshot snapshot = Snapshot.empty();

    @Autowired
    public CursorModelResolver(ObjectMapper objectMapper) {
        this(objectMapper, CursorStateDbPaths.defaultStateDbPath());
    }

    CursorModelResolver(ObjectMapper objectMapper, Path stateDbPath) {
        this.objectMapper = objectMapper;
        this.stateDbPath = stateDbPath;
    }

    public String resolveModel(String composerId, Instant at, String transcriptModel) {
        if (isConcreteModel(transcriptModel)) {
            return transcriptModel.trim();
        }
        if (composerId == null || composerId.isBlank()) {
            return fallback(transcriptModel);
        }
        ComposerModels models = snapshot().composer(composerId);
        Optional<String> timed = models.modelAt(at);
        if (timed.isPresent()) {
            return timed.get();
        }
        if (models.configured().isPresent()) {
            return models.configured().get();
        }
        return models.latestModel().orElseGet(() -> fallback(transcriptModel));
    }

    public List<String> distinctModels(String composerId) {
        if (composerId == null || composerId.isBlank()) {
            return List.of();
        }
        return snapshot().composer(composerId).distinctModels();
    }

    private Snapshot snapshot() {
        Snapshot current = snapshot;
        long now = System.currentTimeMillis();
        long mtime = sourceMtimeMs();
        // Skip expensive reload when the state DB has not changed.
        if (current.loadedAtMs > 0 && mtime == current.sourceMtimeMs()) {
            return current;
        }
        if (now - current.loadedAtMs < MIN_RELOAD_INTERVAL_MS) {
            return current;
        }
        synchronized (this) {
            current = snapshot;
            mtime = sourceMtimeMs();
            if (current.loadedAtMs > 0 && mtime == current.sourceMtimeMs()) {
                return current;
            }
            if (now - current.loadedAtMs < MIN_RELOAD_INTERVAL_MS) {
                return current;
            }
            Snapshot loaded = loadAll(mtime);
            snapshot = loaded;
            return loaded;
        }
    }

    private long sourceMtimeMs() {
        try {
            if (!CursorStateDbPaths.exists(stateDbPath)) {
                return 0L;
            }
            return java.nio.file.Files.getLastModifiedTime(stateDbPath).toMillis();
        } catch (Exception ex) {
            return -1L;
        }
    }

    private Snapshot loadAll(long sourceMtimeMs) {
        if (!CursorStateDbPaths.exists(stateDbPath)) {
            return new Snapshot(Map.of(), System.currentTimeMillis(), sourceMtimeMs);
        }
        Map<String, List<TimedModel>> timelines = new HashMap<>();
        Map<String, String> configured = new HashMap<>();
        String url = "jdbc:sqlite:file:" + stateDbPath.toAbsolutePath() + "?mode=ro";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    """
                    SELECT key, value FROM cursorDiskKV
                    WHERE key LIKE 'bubbleId:%'
                      AND value LIKE '%modelName%'
                    """);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("key");
                    String composerId = composerIdFromBubbleKey(key);
                    if (composerId == null) {
                        continue;
                    }
                    parseBubble(rs.getObject("value")).ifPresent(model ->
                            timelines.computeIfAbsent(composerId, ignored -> new ArrayList<>()).add(model));
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    """
                    SELECT key, value FROM cursorDiskKV
                    WHERE key LIKE 'composerData:%'
                      AND value LIKE '%modelConfig%'
                    """);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("key");
                    String composerId = key.startsWith("composerData:") ? key.substring("composerData:".length()) : null;
                    if (composerId == null || composerId.isBlank()) {
                        continue;
                    }
                    parseConfiguredModel(rs.getObject("value"))
                            .ifPresent(model -> configured.put(composerId, model));
                }
            }
        } catch (Exception ex) {
            log.warn("Cursor model catalog refresh failed: {}", ex.getMessage());
            return new Snapshot(Map.of(), System.currentTimeMillis(), sourceMtimeMs);
        }

        Map<String, ComposerModels> composers = new HashMap<>();
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.addAll(timelines.keySet());
        ids.addAll(configured.keySet());
        for (String id : ids) {
            List<TimedModel> timeline = new ArrayList<>(timelines.getOrDefault(id, List.of()));
            timeline.sort((a, b) -> a.at().compareTo(b.at()));
            composers.put(id, new ComposerModels(timeline, configured.get(id)));
        }
        log.info("Loaded Cursor model catalog for {} composer(s) from {}", composers.size(), stateDbPath);
        return new Snapshot(composers, System.currentTimeMillis(), sourceMtimeMs);
    }

    private static String composerIdFromBubbleKey(String key) {
        // bubbleId:{composerId}:{bubbleId}
        if (key == null || !key.startsWith("bubbleId:")) {
            return null;
        }
        String rest = key.substring("bubbleId:".length());
        int sep = rest.indexOf(':');
        if (sep <= 0) {
            return null;
        }
        return rest.substring(0, sep);
    }

    private Optional<TimedModel> parseBubble(Object raw) {
        try {
            JsonNode node = readJson(raw);
            if (node == null) {
                return Optional.empty();
            }
            if (node.has("type") && node.get("type").isIntegralNumber() && node.get("type").asInt() != 1) {
                return Optional.empty();
            }
            String model = text(node.path("modelInfo").path("modelName"));
            if (!isConcreteModel(model)) {
                return Optional.empty();
            }
            Instant at = Instant.EPOCH;
            String createdAt = text(node.path("createdAt"));
            if (createdAt != null) {
                try {
                    at = Instant.parse(createdAt);
                } catch (Exception ignored) {
                    at = Instant.EPOCH;
                }
            }
            return Optional.of(new TimedModel(at, model.trim()));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Optional<String> parseConfiguredModel(Object raw) {
        try {
            JsonNode node = readJson(raw);
            if (node == null) {
                return Optional.empty();
            }
            String model = text(node.path("modelConfig").path("modelName"));
            if (isConcreteModel(model)) {
                return Optional.of(model.trim());
            }
            JsonNode selected = node.path("modelConfig").path("selectedModels");
            if (selected.isArray()) {
                for (JsonNode entry : selected) {
                    String id = text(entry.path("modelId"));
                    if (isConcreteModel(id)) {
                        return Optional.of(id.trim());
                    }
                }
            }
            return Optional.empty();
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private JsonNode readJson(Object raw) throws Exception {
        if (raw == null) {
            return null;
        }
        if (raw instanceof byte[] bytes) {
            return objectMapper.readTree(bytes);
        }
        return objectMapper.readTree(raw.toString());
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    static boolean isConcreteModel(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        return !PLACEHOLDER_MODELS.contains(model.trim().toLowerCase(Locale.ROOT));
    }

    private static String fallback(String transcriptModel) {
        if (transcriptModel != null && !transcriptModel.isBlank()) {
            return transcriptModel.trim();
        }
        return "cursor";
    }

    private record TimedModel(Instant at, String model) {
    }

    private record Snapshot(Map<String, ComposerModels> composers, long loadedAtMs, long sourceMtimeMs) {
        static Snapshot empty() {
            return new Snapshot(Map.of(), 0L, Long.MIN_VALUE);
        }

        ComposerModels composer(String composerId) {
            return composers.getOrDefault(composerId, ComposerModels.empty());
        }
    }

    private record ComposerModels(List<TimedModel> timeline, String configuredModel) {
        static ComposerModels empty() {
            return new ComposerModels(List.of(), null);
        }

        Optional<String> configured() {
            return Optional.ofNullable(configuredModel).filter(CursorModelResolver::isConcreteModel);
        }

        Optional<String> latestModel() {
            if (timeline.isEmpty()) {
                return configured();
            }
            return Optional.of(timeline.getLast().model());
        }

        Optional<String> modelAt(Instant at) {
            if (timeline.isEmpty()) {
                return Optional.empty();
            }
            if (at == null) {
                return latestModel();
            }
            TimedModel chosen = null;
            for (TimedModel entry : timeline) {
                if (!entry.at().isAfter(at)) {
                    chosen = entry;
                } else {
                    break;
                }
            }
            if (chosen != null) {
                return Optional.of(chosen.model());
            }
            return Optional.of(timeline.getFirst().model());
        }

        List<String> distinctModels() {
            LinkedHashSet<String> models = new LinkedHashSet<>();
            for (TimedModel entry : timeline) {
                models.add(entry.model());
            }
            configured().ifPresent(models::add);
            return List.copyOf(models);
        }
    }
}
