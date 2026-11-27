/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.manager.archive.ArchiveSink;
import com.agentretry.manager.archive.ArchiveSinkFactory;
import com.agentretry.manager.domain.ArchivePolicyEntity;
import com.agentretry.manager.graphql.ArchiveRestoreItem;
import com.agentretry.manager.platform.FeatureGateService;
import com.agentretry.manager.platform.PlatformFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ArchiveRestoreService {

    private final ArchivePolicyService policyService;
    private final ArchiveSinkFactory sinkFactory;
    private final FeatureGateService featureGateService;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public ArchiveRestoreService(
            ArchivePolicyService policyService,
            ArchiveSinkFactory sinkFactory,
            FeatureGateService featureGateService,
            ObjectMapper objectMapper
    ) {
        this.policyService = policyService;
        this.sinkFactory = sinkFactory;
        this.featureGateService = featureGateService;
        this.objectMapper = objectMapper;
    }

    public List<ArchiveRestoreItem> listCold(String dataset, String dayPrefix) throws Exception {
        requireRestore();
        ArchivePolicyEntity policy = policyService.requireEntity();
        ArchiveSink sink = sinkFactory.create(policy);
        try {
            String prefix = "dataset=" + (dataset == null || dataset.isBlank()
                    ? ""
                    : dataset.trim().toLowerCase());
            if (dayPrefix != null && !dayPrefix.isBlank()) {
                prefix = prefix + "/day=" + dayPrefix.trim();
            }
            List<ArchiveRestoreItem> items = new ArrayList<>();
            for (String path : sink.list(prefix)) {
                if (!path.endsWith(".jsonl") && !path.contains("/manifest-")) {
                    continue;
                }
                String[] parts = path.split("/");
                String ds = "";
                String day = "";
                for (String part : parts) {
                    if (part.startsWith("dataset=")) {
                        ds = part.substring("dataset=".length());
                    }
                    if (part.startsWith("day=")) {
                        day = part.substring("day=".length());
                    }
                }
                long bytes = sink.read(path).map(b -> (long) b.length).orElse(0L);
                items.add(new ArchiveRestoreItem(ds, day, path, bytes));
            }
            return items;
        } finally {
            if (sink instanceof AutoCloseable c) {
                c.close();
            }
        }
    }

    public byte[] downloadCold(String path) throws Exception {
        requireRestore();
        ArchivePolicyEntity policy = policyService.requireEntity();
        ArchiveSink sink = sinkFactory.create(policy);
        try {
            Optional<byte[]> data = sink.read(path);
            if (data.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Archive object not found");
            }
            return data.get();
        } finally {
            if (sink instanceof AutoCloseable c) {
                c.close();
            }
        }
    }

    /**
     * Rehydrates token_usage rows from a JSONL archive part (best-effort, idempotent by idempotency_key).
     * Events/tasks restore is download-only in this phase to avoid FK complexity.
     */
    @Transactional
    public int rehydrateTokenUsage(String jsonlPath) throws Exception {
        requireRestore();
        byte[] raw = downloadCold(jsonlPath);
        String text = new String(raw, StandardCharsets.UTF_8);
        int restored = 0;
        for (String line : text.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode node = objectMapper.readTree(line);
            String idempotencyKey = text(node, "idempotencyKey");
            if (idempotencyKey == null) {
                continue;
            }
            Number existing = (Number) entityManager.createQuery(
                            "SELECT COUNT(u) FROM TokenUsageEntity u WHERE u.idempotencyKey = :key")
                    .setParameter("key", idempotencyKey)
                    .getSingleResult();
            if (existing.longValue() > 0) {
                continue;
            }
            // Download-only safety: skip insert without task FK present
            String taskId = text(node, "taskId");
            if (taskId == null) {
                continue;
            }
            Number taskExists = (Number) entityManager.createQuery(
                            "SELECT COUNT(t) FROM TaskEntity t WHERE t.id = :id")
                    .setParameter("id", java.util.UUID.fromString(taskId))
                    .getSingleResult();
            if (taskExists.longValue() == 0) {
                continue;
            }
            entityManager.createNativeQuery("""
                    INSERT INTO token_usage (
                        id, task_id, turn_index, model, input_tokens, output_tokens,
                        cache_read_tokens, cache_write_tokens, reasoning_tokens,
                        estimated_cost_usd, recorded_at, idempotency_key
                    ) VALUES (
                        :id, :taskId, :turnIndex, :model, :inputTokens, :outputTokens,
                        :cacheRead, :cacheWrite, :reasoning, :cost, :recordedAt, :idem
                    )
                    """)
                    .setParameter("id", java.util.UUID.randomUUID())
                    .setParameter("taskId", java.util.UUID.fromString(taskId))
                    .setParameter("turnIndex", node.path("turnIndex").asInt(0))
                    .setParameter("model", text(node, "model"))
                    .setParameter("inputTokens", node.path("inputTokens").asLong(0))
                    .setParameter("outputTokens", node.path("outputTokens").asLong(0))
                    .setParameter("cacheRead", node.path("cacheReadTokens").asLong(0))
                    .setParameter("cacheWrite", node.path("cacheWriteTokens").asLong(0))
                    .setParameter("reasoning", node.path("reasoningTokens").asLong(0))
                    .setParameter("cost", node.path("estimatedCostUsd").isNull() ? null : node.path("estimatedCostUsd").asDouble())
                    .setParameter("recordedAt", java.time.Instant.parse(text(node, "recordedAt")))
                    .setParameter("idem", idempotencyKey)
                    .executeUpdate();
            restored++;
        }
        return restored;
    }

    private void requireRestore() {
        if (!featureGateService.isEnabled(PlatformFeature.ARCHIVE_RESTORE_QUERY)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cold archive restore requires Tier 4 (ARCHIVE_RESTORE_QUERY)");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
