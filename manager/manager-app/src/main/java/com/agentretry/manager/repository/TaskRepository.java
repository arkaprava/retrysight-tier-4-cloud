/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.repository;

import com.agentretry.manager.domain.TaskEntity;
import com.agentretry.contract.SourceTool;
import com.agentretry.contract.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<TaskEntity, UUID>, JpaSpecificationExecutor<TaskEntity> {

    @Query("SELECT t FROM TaskEntity t JOIN FETCH t.agent WHERE t.id = :id")
    Optional<TaskEntity> findByIdWithAgent(@Param("id") UUID id);

    Optional<TaskEntity> findBySourceToolAndExternalSessionId(SourceTool sourceTool, String externalSessionId);

    long countByStatus(TaskStatus status);
}
