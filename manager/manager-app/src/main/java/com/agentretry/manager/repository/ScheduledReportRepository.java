/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.repository;

import com.agentretry.manager.domain.ScheduledReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScheduledReportRepository extends JpaRepository<ScheduledReportEntity, UUID> {
    List<ScheduledReportEntity> findByActiveTrue();
}
