/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.repository;

import com.agentretry.manager.domain.EvalDatasetItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EvalDatasetItemRepository extends JpaRepository<EvalDatasetItemEntity, EvalDatasetItemEntity.Key> {
    List<EvalDatasetItemEntity> findByDatasetIdOrderByAddedAtDesc(UUID datasetId);

    int countByDatasetId(UUID datasetId);

    boolean existsByDatasetIdAndTaskId(UUID datasetId, UUID taskId);
}
