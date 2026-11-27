/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.repository;

import com.agentretry.manager.domain.IdentityProviderConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdentityProviderConfigRepository extends JpaRepository<IdentityProviderConfigEntity, UUID> {

    Optional<IdentityProviderConfigEntity> findFirstByEnabledTrue();

    Optional<IdentityProviderConfigEntity> findFirstByOrderByUpdatedAtDesc();
}
