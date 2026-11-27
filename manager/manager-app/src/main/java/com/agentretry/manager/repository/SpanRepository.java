/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.repository;

import com.agentretry.manager.domain.SpanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SpanRepository extends JpaRepository<SpanEntity, UUID> {
    List<SpanEntity> findByTaskIdOrderByStartAtAsc(UUID taskId);

    @Query("""
            SELECT s FROM SpanEntity s
            WHERE (:taskId IS NULL OR s.taskId = :taskId)
              AND (:sourceTool IS NULL OR s.sourceTool = :sourceTool)
              AND s.startAt >= :from AND s.startAt <= :to
            ORDER BY s.startAt DESC
            """)
    List<SpanEntity> search(
            @Param("taskId") UUID taskId,
            @Param("sourceTool") String sourceTool,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    boolean existsByExternalTraceIdAndExternalSpanId(String externalTraceId, String externalSpanId);
}
