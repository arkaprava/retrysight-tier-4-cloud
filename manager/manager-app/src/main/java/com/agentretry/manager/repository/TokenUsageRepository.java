/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.repository;

import com.agentretry.manager.domain.TokenUsageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TokenUsageRepository extends JpaRepository<TokenUsageEntity, UUID> {

    List<TokenUsageEntity> findByTaskIdOrderByTurnIndexAsc(UUID taskId);

    List<TokenUsageEntity> findByTaskIdInOrderByTaskIdAscTurnIndexAsc(List<UUID> taskIds);

    boolean existsByIdempotencyKey(String idempotencyKey);

    @Query("""
            SELECT COALESCE(SUM(t.inputTokens + t.outputTokens + t.cacheReadTokens
                + t.cacheWriteTokens + t.reasoningTokens), 0)
            FROM TokenUsageEntity t
            WHERE t.task.id = :taskId
            """)
    long sumTotalTokensByTaskId(@Param("taskId") UUID taskId);
}
