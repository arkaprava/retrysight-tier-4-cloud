/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.repository;

import com.agentretry.manager.domain.TaskEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaskEventRepository extends JpaRepository<TaskEventEntity, UUID> {

    List<TaskEventEntity> findByTaskIdOrderBySequenceNumAsc(UUID taskId);

    List<TaskEventEntity> findByTaskIdInAndRetryTrueOrderBySequenceNumAsc(Collection<UUID> taskIds);

    boolean existsByIdempotencyKey(String idempotencyKey);

    long deleteByOccurredAtBefore(Instant cutoff);
}
