/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import com.agentretry.manager.platform.FeatureGateService;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class PlatformGraphqlController {

    private final FeatureGateService featureGateService;

    public PlatformGraphqlController(FeatureGateService featureGateService) {
        this.featureGateService = featureGateService;
    }

    @QueryMapping
    public PlatformFeatures platformFeatures() {
        return featureGateService.snapshot();
    }

    @QueryMapping
    public List<SourceToolInfo> sourceTools() {
        return featureGateService.availableSourceTools();
    }
}
