/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.repository;

import com.agentretry.manager.domain.UiUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UiUserRepository extends JpaRepository<UiUserEntity, UUID> {

    Optional<UiUserEntity> findByEmailAndActiveTrue(String email);

    Optional<UiUserEntity> findByExternalSubject(String externalSubject);
}
