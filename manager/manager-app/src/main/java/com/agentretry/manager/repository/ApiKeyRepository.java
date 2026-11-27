/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.repository;

import com.agentretry.manager.domain.ApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, UUID> {

    Optional<ApiKeyEntity> findByKeyHashAndActiveTrue(String keyHash);

    Optional<ApiKeyEntity> findByKeyHash(String keyHash);

    @Query("""
            SELECT k FROM ApiKeyEntity k
            WHERE k.keyHash = :hash
              AND (
                k.active = true
                OR (k.graceUntil IS NOT NULL AND k.graceUntil > :now)
              )
            """)
    Optional<ApiKeyEntity> findValidByKeyHash(@Param("hash") String hash, @Param("now") Instant now);
}
