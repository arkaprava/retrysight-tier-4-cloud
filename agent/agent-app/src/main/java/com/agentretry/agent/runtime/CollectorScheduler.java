/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.runtime;

import com.agentretry.agent.collector.SessionCollector;
import com.agentretry.agent.config.AgentProperties;
import com.agentretry.agent.discovery.AgentHealthReporter;
import com.agentretry.agent.discovery.CollectorDiscoveryService;
import com.agentretry.agent.uploader.ManagerUploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@ConditionalOnProperty(name = "agent.cli.command", havingValue = "run", matchIfMissing = true)
public class CollectorScheduler {

    private static final Logger log = LoggerFactory.getLogger(CollectorScheduler.class);

    private final List<SessionCollector> collectors;
    private final ManagerUploader managerUploader;
    private final AgentProperties properties;
    private final CollectorDiscoveryService discoveryService;
    private final AgentHealthReporter healthReporter;
    private final ExecutorService scanExecutor = Executors.newFixedThreadPool(
            4,
            r -> {
                Thread t = new Thread(r, "collector-scan");
                t.setDaemon(true);
                return t;
            }
    );

    public CollectorScheduler(
            List<SessionCollector> collectors,
            ManagerUploader managerUploader,
            AgentProperties properties,
            CollectorDiscoveryService discoveryService,
            AgentHealthReporter healthReporter
    ) {
        this.collectors = collectors;
        this.managerUploader = managerUploader;
        this.properties = properties;
        this.discoveryService = discoveryService;
        this.healthReporter = healthReporter;
    }

    /** Keep uploads moving even when a collector scan is slow (e.g. Cursor state DB). */
    @Scheduled(initialDelay = 1000, fixedDelayString = "${agent.upload.batch-interval-seconds:2}000")
    public void upload() {
        managerUploader.uploadBatch();
    }

    @Scheduled(initialDelay = 1500, fixedDelayString = "${agent.upload.scan-interval-seconds:3}000")
    public void collect() {
        List<CompletableFuture<Void>> futures = collectors.stream()
                .filter(SessionCollector::isEnabled)
                .map(collector -> CompletableFuture.runAsync(() -> {
                    try {
                        collector.scan();
                        Duration idle = idleThreshold();
                        if (!idle.isZero()) {
                            collector.expireIdleSessions(idle);
                        }
                    } catch (Exception ex) {
                        log.warn("Collector {} scan failed: {}", collector.sourceTool(), ex.getMessage());
                    }
                }, scanExecutor))
                .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    @Scheduled(initialDelay = 5000, fixedDelayString = "${agent.discovery.refresh-interval-seconds:300}000")
    public void refreshDiscovery() {
        if (!properties.getPlatform().isAutoDiscovery()) {
            return;
        }
        discoveryService.refresh();
        healthReporter.reportPeriodic();
    }

    @Scheduled(initialDelay = 2000, fixedDelay = 30000)
    public void logStatus() {
        long enabled = collectors.stream().filter(SessionCollector::isEnabled).count();
        log.info("Agent collector running — {} collector(s) enabled", enabled);
    }

    private Duration idleThreshold() {
        int minutes = properties.getSession().getIdleCompleteMinutes();
        if (minutes <= 0) {
            return Duration.ZERO;
        }
        return Duration.ofMinutes(minutes);
    }
}
