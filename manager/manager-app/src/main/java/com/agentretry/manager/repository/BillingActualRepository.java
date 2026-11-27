/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.repository;

import com.agentretry.manager.domain.BillingActualEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BillingActualRepository extends JpaRepository<BillingActualEntity, UUID> {

    @Query("""
            SELECT b FROM BillingActualEntity b
            WHERE b.periodStart >= :from AND b.periodEnd <= :to
            ORDER BY b.periodStart ASC
            """)
    List<BillingActualEntity> findByPeriodRange(@Param("from") Instant from, @Param("to") Instant to);
}
