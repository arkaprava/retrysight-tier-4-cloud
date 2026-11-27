/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.repository;

import com.agentretry.manager.domain.ModelPricingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelPricingRepository extends JpaRepository<ModelPricingEntity, String> {
}
