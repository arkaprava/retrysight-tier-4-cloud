/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.manager.archive.ArchiveDataset;
import com.agentretry.manager.archive.ArchiveDestinationType;
import com.agentretry.manager.archive.ArchiveManifest;
import com.agentretry.manager.archive.ArchiveMode;
import com.agentretry.manager.archive.ArchiveSink;
import com.agentretry.manager.archive.ArchiveSinkFactory;
import com.agentretry.manager.domain.ArchivePolicyEntity;
import com.agentretry.manager.domain.ArchiveRunEntity;
import com.agentretry.manager.graphql.ArchiveRun;
import com.agentretry.manager.platform.FeatureGateService;
import com.agentretry.manager.platform.PlatformFeature;
import com.agentretry.manager.repository.ArchiveRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ArchiveOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ArchiveOrchestrator.class);
    private static final int BATCH_SIZE = 500;

    private final ArchivePolicyService policyService;
    private final ArchiveRunRepository runRepository;
    private final ArchiveSinkFactory sinkFactory;
    private final FeatureGateService featureGateService;
    private final ObjectMapper objectMapper;
    private final RollupService rollupService;

    @PersistenceContext
    private EntityManager entityManager;

    public ArchiveOrchestrator(
            ArchivePolicyService policyService,
            ArchiveRunRepository runRepository,
            ArchiveSinkFactory sinkFactory,
            FeatureGateService featureGateService,
            ObjectMapper objectMapper,
            RollupService rollupService
    ) {
        this.policyService = policyService;
        this.runRepository = runRepository;
        this.sinkFactory = sinkFactory;
        this.featureGateService = featureGateService;
        this.objectMapper = objectMapper;
        this.rollupService = rollupService;
    }

    @Scheduled(cron = "${manager.archive.schedule-cron:0 30 2 * * *}")
    public void scheduledRun() {
        ArchivePolicyEntity policy = policyService.requireEntity();
        if (!policy.isEnabled()) {
            return;
        }
        runArchive(false);
    }

    @Transactional
    public ArchiveRun runArchive(boolean dryRun) {
        ArchivePolicyEntity policy = policyService.requireEntity();
        ArchiveMode mode = ArchiveMode.valueOf(policy.getMode());
        ArchiveDestinationType dest = ArchiveDestinationType.valueOf(policy.getDestinationType());

        if (mode != ArchiveMode.DELETE_ONLY) {
            requireArchiveDestination(dest);
        } else if (!featureGateService.isEnabled(PlatformFeature.RETENTION_PURGE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Retention purge is disabled for this tier");
        }

        ArchiveRunEntity run = new ArchiveRunEntity();
        run.setId(UUID.randomUUID());
        run.setStartedAt(Instant.now());
        run.setStatus("RUNNING");
        run.setDryRun(dryRun);
        run.setMode(mode.name());
        run.setDestinationType(dest.name());
        run.setDatasets(policy.getDatasets());
        run.setRowsExported(0);
        run.setRowsDeleted(0);
        run.setBytesWritten(0);
        runRepository.save(run);

        List<String> manifestNotes = new ArrayList<>();
        ArchiveSink sink = null;
        try {
            boolean needsArchive = mode == ArchiveMode.ARCHIVE_THEN_DELETE || mode == ArchiveMode.ARCHIVE_ONLY;
            if (needsArchive) {
                sink = sinkFactory.create(policy);
            }

            for (String datasetName : policy.getDatasets().split(",")) {
                ArchiveDataset dataset = ArchiveDataset.valueOf(datasetName.trim());
                DatasetResult result = processDataset(policy, dataset, mode, sink, dryRun);
                run.setRowsExported(run.getRowsExported() + result.exported());
                run.setRowsDeleted(run.getRowsDeleted() + result.deleted());
                run.setBytesWritten(run.getBytesWritten() + result.bytes());
                manifestNotes.addAll(result.notes());
            }

            if (!dryRun && featureGateService.isEnabled(PlatformFeature.ROLLUP_ANALYTICS)) {
                rollupService.refreshRecentDays(14);
            }

            run.setStatus("SUCCESS");
            run.setManifestSummary(String.join("\n", manifestNotes));
        } catch (Exception ex) {
            log.warn("Archive run failed: {}", ex.getMessage(), ex);
            run.setStatus("FAILED");
            run.setErrorMessage(ex.getMessage());
        } finally {
            run.setFinishedAt(Instant.now());
            runRepository.save(run);
            if (sink instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception ignored) {
                }
            }
        }
        return toGraphql(run);
    }

    @Transactional(readOnly = true)
    public List<ArchiveRun> recentRuns() {
        return runRepository.findTop50ByOrderByStartedAtDesc().stream().map(this::toGraphql).toList();
    }

    private DatasetResult processDataset(
            ArchivePolicyEntity policy,
            ArchiveDataset dataset,
            ArchiveMode mode,
            ArchiveSink sink,
            boolean dryRun
    ) throws Exception {
        Instant cutoff = cutoffFor(policy, dataset);
        Instant afterTs = Instant.EPOCH;
        UUID afterId = new UUID(0L, 0L);
        long exported = 0;
        long deleted = 0;
        long bytes = 0;
        List<String> notes = new ArrayList<>();

        while (true) {
            List<Object[]> rows = fetchBatch(dataset, cutoff, afterTs, afterId);
            if (rows.isEmpty()) {
                break;
            }

            Map<String, List<Map<String, Object>>> byDay = new LinkedHashMap<>();
            List<UUID> ids = new ArrayList<>();
            for (Object[] row : rows) {
                UUID id = (UUID) row[0];
                Instant ts = (Instant) row[1];
                ids.add(id);
                String day = ts.atZone(ZoneOffset.UTC).toLocalDate().toString();
                byDay.computeIfAbsent(day, ignored -> new ArrayList<>()).add(rowToMap(dataset, row));
            }
            Object[] last = rows.getLast();
            afterTs = (Instant) last[1];
            afterId = (UUID) last[0];

            if (mode != ArchiveMode.DELETE_ONLY && sink != null) {
                for (Map.Entry<String, List<Map<String, Object>>> entry : byDay.entrySet()) {
                    byte[] payload = toJsonl(entry.getValue());
                    String partPath = "dataset=" + dataset.name().toLowerCase()
                            + "/day=" + entry.getKey()
                            + "/part-" + UUID.randomUUID() + ".jsonl";
                    if (!dryRun) {
                        bytes += sink.write(partPath, payload);
                        ArchiveManifest manifest = new ArchiveManifest(
                                ArchiveManifest.SCHEMA_VERSION,
                                dataset.name(),
                                entry.getKey(),
                                entry.getValue().size(),
                                sha256(payload),
                                payload.length,
                                Instant.now(),
                                List.of(partPath)
                        );
                        String manifestPath = "dataset=" + dataset.name().toLowerCase()
                                + "/day=" + entry.getKey()
                                + "/manifest-" + UUID.randomUUID() + ".json";
                        bytes += sink.write(manifestPath, objectMapper.writeValueAsBytes(manifest));
                        notes.add(manifestPath + " rows=" + entry.getValue().size());
                    } else {
                        notes.add("dry-run " + dataset + " " + entry.getKey() + " rows=" + entry.getValue().size());
                    }
                    exported += entry.getValue().size();
                }
            } else {
                exported += rows.size();
            }

            boolean shouldDelete = !dryRun
                    && !policy.isLegalHold()
                    && (mode == ArchiveMode.DELETE_ONLY || mode == ArchiveMode.ARCHIVE_THEN_DELETE);
            if (shouldDelete) {
                deleted += deleteIds(dataset, ids);
            }

            if (dryRun) {
                // One batch is enough for a dry-run preview.
                break;
            }
            if (rows.size() < BATCH_SIZE) {
                break;
            }
            entityManager.clear();
        }

        return new DatasetResult(exported, deleted, bytes, notes);
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> fetchBatch(ArchiveDataset dataset, Instant cutoff, Instant afterTs, UUID afterId) {
        return switch (dataset) {
            case TASK_EVENTS -> entityManager.createQuery("""
                            SELECT e.id, e.occurredAt, e.eventType, e.retry, e.sequenceNum, e.idempotencyKey, e.payload, t.id
                            FROM TaskEventEntity e JOIN e.task t
                            WHERE e.occurredAt < :cutoff
                              AND (e.occurredAt > :afterTs OR (e.occurredAt = :afterTs AND e.id > :afterId))
                            ORDER BY e.occurredAt ASC, e.id ASC
                            """)
                    .setParameter("cutoff", cutoff)
                    .setParameter("afterTs", afterTs)
                    .setParameter("afterId", afterId)
                    .setMaxResults(BATCH_SIZE)
                    .getResultList();
            case TOKEN_USAGE -> entityManager.createQuery("""
                            SELECT u.id, u.recordedAt, u.turnIndex, u.model, u.inputTokens, u.outputTokens,
                                   u.cacheReadTokens, u.cacheWriteTokens, u.reasoningTokens, u.estimatedCostUsd,
                                   u.idempotencyKey, t.id
                            FROM TokenUsageEntity u JOIN u.task t
                            WHERE u.recordedAt < :cutoff
                              AND (u.recordedAt > :afterTs OR (u.recordedAt = :afterTs AND u.id > :afterId))
                            ORDER BY u.recordedAt ASC, u.id ASC
                            """)
                    .setParameter("cutoff", cutoff)
                    .setParameter("afterTs", afterTs)
                    .setParameter("afterId", afterId)
                    .setMaxResults(BATCH_SIZE)
                    .getResultList();
            case TASKS -> entityManager.createQuery("""
                            SELECT t.id, t.startedAt, t.externalSessionId, t.sourceTool, t.description,
                                   t.status, t.retryCount, t.completedAt, t.metadata
                            FROM TaskEntity t
                            WHERE t.startedAt < :cutoff
                              AND (t.startedAt > :afterTs OR (t.startedAt = :afterTs AND t.id > :afterId))
                            ORDER BY t.startedAt ASC, t.id ASC
                            """)
                    .setParameter("cutoff", cutoff)
                    .setParameter("afterTs", afterTs)
                    .setParameter("afterId", afterId)
                    .setMaxResults(BATCH_SIZE)
                    .getResultList();
        };
    }

    private int deleteIds(ArchiveDataset dataset, List<UUID> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        return switch (dataset) {
            case TASK_EVENTS -> entityManager.createQuery("DELETE FROM TaskEventEntity e WHERE e.id IN :ids")
                    .setParameter("ids", ids)
                    .executeUpdate();
            case TOKEN_USAGE -> entityManager.createQuery("DELETE FROM TokenUsageEntity u WHERE u.id IN :ids")
                    .setParameter("ids", ids)
                    .executeUpdate();
            case TASKS -> {
                // Only delete tasks that have no remaining events/tokens
                int removed = 0;
                for (UUID id : ids) {
                    Long events = (Long) entityManager.createQuery(
                                    "SELECT COUNT(e) FROM TaskEventEntity e WHERE e.task.id = :id")
                            .setParameter("id", id)
                            .getSingleResult();
                    Long tokens = (Long) entityManager.createQuery(
                                    "SELECT COUNT(u) FROM TokenUsageEntity u WHERE u.task.id = :id")
                            .setParameter("id", id)
                            .getSingleResult();
                    if (events == 0 && tokens == 0) {
                        removed += entityManager.createQuery("DELETE FROM TaskEntity t WHERE t.id = :id")
                                .setParameter("id", id)
                                .executeUpdate();
                    }
                }
                yield removed;
            }
        };
    }

    private Map<String, Object> rowToMap(ArchiveDataset dataset, Object[] row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", row[0] != null ? row[0].toString() : null);
        return switch (dataset) {
            case TASK_EVENTS -> {
                map.put("occurredAt", row[1] != null ? row[1].toString() : null);
                map.put("eventType", row[2] != null ? row[2].toString() : null);
                map.put("retry", row[3]);
                map.put("sequenceNum", row[4]);
                map.put("idempotencyKey", row[5]);
                map.put("payload", row[6]);
                map.put("taskId", row[7] != null ? row[7].toString() : null);
                yield map;
            }
            case TOKEN_USAGE -> {
                map.put("recordedAt", row[1] != null ? row[1].toString() : null);
                map.put("turnIndex", row[2]);
                map.put("model", row[3]);
                map.put("inputTokens", row[4]);
                map.put("outputTokens", row[5]);
                map.put("cacheReadTokens", row[6]);
                map.put("cacheWriteTokens", row[7]);
                map.put("reasoningTokens", row[8]);
                map.put("estimatedCostUsd", row[9]);
                map.put("idempotencyKey", row[10]);
                map.put("taskId", row[11] != null ? row[11].toString() : null);
                yield map;
            }
            case TASKS -> {
                map.put("startedAt", row[1] != null ? row[1].toString() : null);
                map.put("externalSessionId", row[2]);
                map.put("sourceTool", row[3] != null ? row[3].toString() : null);
                map.put("description", row[4]);
                map.put("status", row[5] != null ? row[5].toString() : null);
                map.put("retryCount", row[6]);
                map.put("completedAt", row[7] != null ? row[7].toString() : null);
                map.put("metadata", row[8]);
                yield map;
            }
        };
    }

    private byte[] toJsonl(List<Map<String, Object>> rows) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> row : rows) {
            sb.append(objectMapper.writeValueAsString(row)).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Instant cutoffFor(ArchivePolicyEntity policy, ArchiveDataset dataset) {
        int days = switch (dataset) {
            case TASK_EVENTS -> policy.getTaskEventsDays();
            case TOKEN_USAGE -> policy.getTokenUsageDays();
            case TASKS -> policy.getTaskHeadersDays();
        };
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }

    private void requireArchiveDestination(ArchiveDestinationType dest) {
        boolean ok = switch (dest) {
            case LOCAL_FS -> featureGateService.isEnabled(PlatformFeature.ARCHIVE_LOCAL);
            case NETWORK_FS -> featureGateService.isEnabled(PlatformFeature.ARCHIVE_NETWORK);
            case S3 -> featureGateService.isEnabled(PlatformFeature.ARCHIVE_OBJECT_STORAGE);
        };
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Archive destination " + dest + " requires a higher installation tier");
        }
    }

    private ArchiveRun toGraphql(ArchiveRunEntity run) {
        return new ArchiveRun(
                run.getId(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getStatus(),
                run.isDryRun(),
                run.getMode(),
                run.getDestinationType(),
                run.getRowsExported(),
                run.getRowsDeleted(),
                run.getBytesWritten(),
                run.getDatasets(),
                run.getErrorMessage(),
                run.getManifestSummary()
        );
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(data));
    }

    private record DatasetResult(long exported, long deleted, long bytes, List<String> notes) {
    }
}
