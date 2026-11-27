/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.repository;

import com.agentretry.manager.domain.EvalRunEntity;
import com.agentretry.manager.eval.EvalRunStatus;
import com.agentretry.manager.eval.EvalTrigger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EvalRunRepository extends JpaRepository<EvalRunEntity, UUID> {
    List<EvalRunEntity> findByTaskIdOrderByCreatedAtDesc(UUID taskId);

    List<EvalRunEntity> findByTaskIdAndDefinitionIdOrderByCreatedAtDesc(UUID taskId, UUID definitionId);

    boolean existsByTaskIdAndDefinitionIdAndTriggeredBy(UUID taskId, UUID definitionId, EvalTrigger triggeredBy);

    List<EvalRunEntity> findByCreatedAtBetweenAndStatusOrderByCreatedAtAsc(
            Instant from,
            Instant to,
            EvalRunStatus status
    );

    @Query("""
            SELECT r FROM EvalRunEntity r
            WHERE r.createdAt >= :from AND r.createdAt <= :to AND r.status = :status
            ORDER BY r.createdAt ASC
            """)
    List<EvalRunEntity> findCompletedInRange(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("status") EvalRunStatus status
    );
}
