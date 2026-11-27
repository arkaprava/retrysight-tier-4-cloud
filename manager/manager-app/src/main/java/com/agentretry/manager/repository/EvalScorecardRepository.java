/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.repository;

import com.agentretry.manager.domain.EvalScorecardEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EvalScorecardRepository extends JpaRepository<EvalScorecardEntity, UUID> {
    List<EvalScorecardEntity> findByTaskIdOrderByCreatedAtDesc(UUID taskId);
    List<EvalScorecardEntity> findAllByOrderByCreatedAtDesc();
}
