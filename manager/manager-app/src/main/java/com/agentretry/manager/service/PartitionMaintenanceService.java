/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.manager.platform.FeatureGateService;
import com.agentretry.manager.platform.PlatformFeature;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tracks logical monthly hot-data periods. Full native PG partitioning of existing
 * tables is a DBA migration; this service maintains the registry and future windows.
 */
@Service
public class PartitionMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(PartitionMaintenanceService.class);

    private final FeatureGateService featureGateService;

    @PersistenceContext
    private EntityManager entityManager;

    public PartitionMaintenanceService(FeatureGateService featureGateService) {
        this.featureGateService = featureGateService;
    }

    @Scheduled(cron = "0 0 4 1 * *")
    @Transactional
    public void ensureUpcomingPeriods() {
        if (!featureGateService.isEnabled(PlatformFeature.PARTITION_MAINTENANCE)) {
            return;
        }
        LocalDate month = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        for (int i = 0; i < 3; i++) {
            LocalDate start = month.plusMonths(i);
            LocalDate end = start.plusMonths(1);
            ensurePeriod("TASK_EVENTS", start, end);
            ensurePeriod("TOKEN_USAGE", start, end);
        }
        log.info("Partition registry updated for next 3 months");
    }

    @Transactional(readOnly = true)
    public List<PartitionInfo> listPartitions() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT id, dataset, period_start, period_end, status, notes, created_at
                FROM hot_data_partitions
                ORDER BY period_start DESC
                """)
                .getResultList();
        List<PartitionInfo> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new PartitionInfo(
                    UUID.fromString(row[0].toString()),
                    row[1].toString(),
                    row[2].toString(),
                    row[3].toString(),
                    row[4].toString(),
                    row[5] != null ? row[5].toString() : null,
                    row[6].toString()
            ));
        }
        return result;
    }

    private void ensurePeriod(String dataset, LocalDate start, LocalDate end) {
        Number count = (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM hot_data_partitions
                WHERE dataset = :dataset AND period_start = :start
                """)
                .setParameter("dataset", dataset)
                .setParameter("start", start)
                .getSingleResult();
        if (count.longValue() > 0) {
            return;
        }
        entityManager.createNativeQuery("""
                INSERT INTO hot_data_partitions (id, dataset, period_start, period_end, status, notes, created_at)
                VALUES (:id, :dataset, :start, :end, 'PLANNED', :notes, :createdAt)
                """)
                .setParameter("id", UUID.randomUUID())
                .setParameter("dataset", dataset)
                .setParameter("start", start)
                .setParameter("end", end)
                .setParameter("notes", "Logical monthly window for archive/purge batching")
                .setParameter("createdAt", Instant.now())
                .executeUpdate();
    }

    public record PartitionInfo(
            UUID id,
            String dataset,
            String periodStart,
            String periodEnd,
            String status,
            String notes,
            String createdAt
    ) {
    }
}
