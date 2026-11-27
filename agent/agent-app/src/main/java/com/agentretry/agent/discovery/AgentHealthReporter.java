/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.discovery;

import com.agentretry.agent.collector.ParseHealthTracker;
import com.agentretry.agent.collector.SessionCollector;
import com.agentretry.agent.config.AgentProperties;
import com.agentretry.agent.runtime.SingleInstanceLock;
import com.agentretry.contract.SourceTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AgentHealthReporter {

    private static final Logger log = LoggerFactory.getLogger(AgentHealthReporter.class);

    private final AgentProperties properties;
    private final CollectorDiscoveryService discoveryService;
    private final ParseHealthTracker parseHealthTracker;
    private final List<SessionCollector> collectors;
    private final SingleInstanceLock singleInstanceLock;

    public AgentHealthReporter(
            AgentProperties properties,
            CollectorDiscoveryService discoveryService,
            ParseHealthTracker parseHealthTracker,
            List<SessionCollector> collectors,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            SingleInstanceLock singleInstanceLock
    ) {
        this.properties = properties;
        this.discoveryService = discoveryService;
        this.parseHealthTracker = parseHealthTracker;
        this.collectors = collectors;
        this.singleInstanceLock = singleInstanceLock;
    }

    public void reportStartup() {
        report("startup");
    }

    public void reportPeriodic() {
        report("periodic");
    }

    private void report(String phase) {
        List<CollectorDiscoveryService.Result> results = properties.getPlatform().isAutoDiscovery()
                ? discoveryService.refresh()
                : discoveryService.lastResults();

        log.info("Agent health ({}) autoDiscovery={} singleInstance={} configuredEmail={}",
                phase,
                properties.getPlatform().isAutoDiscovery(),
                singleInstanceLock != null && singleInstanceLock.isHeld(),
                properties.getUser().getEmail() != null && !properties.getUser().getEmail().isBlank());

        if (properties.getPlatform().isAutoDiscovery()) {
            for (CollectorDiscoveryService.Result result : results) {
                log.info("  collector {} {} path={} ({})",
                        result.configType(),
                        result.status(),
                        result.watchPath(),
                        result.detail());
            }
        }

        long enabled = collectors.stream().filter(SessionCollector::isEnabled).count();
        log.info("  {} collector(s) enabled", enabled);

        for (SourceTool tool : SourceTool.values()) {
            long parseErrors = parseHealthTracker.parseErrors(tool);
            if (parseErrors > 0) {
                log.warn("  parse errors {}={}", tool, parseErrors);
            }
        }
    }
}
