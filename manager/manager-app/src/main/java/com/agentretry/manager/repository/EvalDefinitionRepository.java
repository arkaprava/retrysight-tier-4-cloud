/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.repository;

import com.agentretry.manager.domain.EvalDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvalDefinitionRepository extends JpaRepository<EvalDefinitionEntity, UUID> {
    List<EvalDefinitionEntity> findAllByOrderByNameAsc();

    List<EvalDefinitionEntity> findByEnabledTrueOrderByNameAsc();

    Optional<EvalDefinitionEntity> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
