/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.support;

import com.agentretry.manager.repository.ModelPricingRepository;
import com.agentretry.manager.service.ModelPricingService;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class TestModelPricing {

    private TestModelPricing() {
    }

    public static ModelPricingService create() {
        ModelPricingRepository repository = mock(ModelPricingRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        ModelPricingService service = new ModelPricingService(repository);
        service.refreshDbOverrides();
        return service;
    }
}
