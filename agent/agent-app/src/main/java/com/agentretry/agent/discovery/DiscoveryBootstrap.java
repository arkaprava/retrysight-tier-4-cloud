/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.discovery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "agent.cli.command", havingValue = "run", matchIfMissing = true)
@DependsOn("collectorAutoDiscovery")
public class DiscoveryBootstrap {

    private final CollectorDiscoveryService discoveryService;
    private final AgentHealthReporter healthReporter;

    public DiscoveryBootstrap(CollectorDiscoveryService discoveryService, AgentHealthReporter healthReporter) {
        this.discoveryService = discoveryService;
        this.healthReporter = healthReporter;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        discoveryService.refresh();
        healthReporter.reportStartup();
    }
}
