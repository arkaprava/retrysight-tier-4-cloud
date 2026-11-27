/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.repository;

import com.agentretry.manager.domain.AnnotationQueueItemEntity;
import com.agentretry.manager.eval.AnnotationQueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnnotationQueueRepository extends JpaRepository<AnnotationQueueItemEntity, UUID> {
    List<AnnotationQueueItemEntity> findByStatusOrderByCreatedAtDesc(AnnotationQueueStatus status);

    List<AnnotationQueueItemEntity> findByTaskIdOrderByCreatedAtDesc(UUID taskId);

    boolean existsByTaskIdAndStatus(UUID taskId, AnnotationQueueStatus status);
}
