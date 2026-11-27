/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.uploader;

import com.agentretry.agent.config.AgentProperties;
import com.agentretry.agent.discovery.CollectorDiscoveryService;
import com.agentretry.agent.identity.AgentIdentityStore;
import com.agentretry.agent.identity.IdeUserInfoResolver;
import com.agentretry.contract.AgentHeartbeatRequest;
import com.agentretry.contract.CollectorDiscoveryEntry;
import com.agentretry.contract.CollectorDiscoveryStatus;
import com.agentretry.contract.IngestBatchRequest;
import com.agentretry.contract.IngestBatchResponse;
import com.agentretry.contract.ParseErrorEntry;
import com.agentretry.contract.SourceTool;
import com.agentretry.agent.collector.ParseHealthTracker;
import com.agentretry.agent.collector.SessionCollector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class ManagerUploader {

    private static final Logger log = LoggerFactory.getLogger(ManagerUploader.class);
    /** Cap work per tick so a huge backlog cannot monopolize the upload thread forever. */
    private static final int MAX_BATCHES_PER_TICK = 8;

    private final WebClient managerWebClient;
    private final EventBuffer eventBuffer;
    private final AgentProperties properties;
    private final List<SessionCollector> collectors;
    private final IdeUserInfoResolver userInfoResolver;
    private final AgentIdentityStore identityStore;
    private final ObjectMapper objectMapper;
    private final CollectorDiscoveryService discoveryService;
    private final ParseHealthTracker parseHealthTracker;

    public ManagerUploader(
            WebClient managerWebClient,
            EventBuffer eventBuffer,
            AgentProperties properties,
            List<SessionCollector> collectors,
            IdeUserInfoResolver userInfoResolver,
            AgentIdentityStore identityStore,
            ObjectMapper objectMapper,
            CollectorDiscoveryService discoveryService,
            ParseHealthTracker parseHealthTracker
    ) {
        this.managerWebClient = managerWebClient;
        this.eventBuffer = eventBuffer;
        this.properties = properties;
        this.collectors = collectors;
        this.userInfoResolver = userInfoResolver;
        this.identityStore = identityStore;
        this.objectMapper = objectMapper;
        this.discoveryService = discoveryService;
        this.parseHealthTracker = parseHealthTracker;
    }

    public void uploadBatch() {
        int batchSize = properties.getUpload().getMaxBatchSize();
        int uploaded = 0;
        boolean sentAnything = false;

        for (int round = 0; round < MAX_BATCHES_PER_TICK; round++) {
            EventBuffer.DrainBatch batch = eventBuffer.drain(batchSize);
            if (batch.isEmpty()) {
                break;
            }

            IngestBatchRequest request = new IngestBatchRequest(buildHeartbeat(), null, batch.items());
            try {
                IngestBatchResponse response = withAgentId(managerWebClient.post().uri("/api/v1/ingest/batch"))
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(IngestBatchResponse.class)
                        .block();

                if (response != null) {
                    rememberAgentId(response.agentId());
                    List<Long> ackIds = UploadAckPlanner.idsToAcknowledge(batch, response);
                    eventBuffer.acknowledge(ackIds);
                    sentAnything = true;
                    uploaded += ackIds.size();
                    if (!ackIds.isEmpty()) {
                        int errorCount = response.errors() == null ? 0 : response.errors().size();
                        log.info("Uploaded batch: accepted={}, skipped={}, errors={}, acked={}",
                                response.accepted(), response.skipped(), errorCount, ackIds.size());
                    }
                    if (response.errors() != null && !response.errors().isEmpty()) {
                        log.warn("Manager rejected {} item(s) — retained for retry", response.errors().size());
                    }
                } else {
                    eventBuffer.acknowledge(batch.ids());
                    sentAnything = true;
                    uploaded += batch.items().size();
                }
            } catch (Exception ex) {
                log.warn("Failed to upload batch to manager — events retained for retry", ex);
                // Keep remaining queue; do not continue draining while manager is down.
                break;
            }

            if (batch.items().size() < batchSize) {
                break;
            }
        }

        if (!sentAnything) {
            sendHeartbeatOnly();
        } else if (uploaded > 0) {
            log.debug("Upload tick drained {} item(s)", uploaded);
        }
    }

    private void sendHeartbeatOnly() {
        try {
            String body = withAgentId(managerWebClient.post().uri("/api/v1/ingest/heartbeat"))
                    .bodyValue(buildHeartbeat())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            rememberAgentIdFromJson(body);
        } catch (Exception ex) {
            log.debug("Heartbeat failed: {}", ex.getMessage());
        }
    }

    private WebClient.RequestBodySpec withAgentId(WebClient.RequestBodySpec spec) {
        return identityStore.getAgentId()
                .map(id -> spec.header("X-Agent-Id", id.toString()))
                .orElse(spec);
    }

    private void rememberAgentId(UUID agentId) {
        if (agentId != null) {
            identityStore.remember(agentId);
        }
    }

    private void rememberAgentIdFromJson(String body) {
        if (body == null || body.isBlank()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.hasNonNull("agentId")) {
                identityStore.remember(UUID.fromString(node.get("agentId").asText()));
            }
        } catch (Exception ex) {
            log.debug("Could not parse agentId from heartbeat response: {}", ex.getMessage());
        }
    }

    private AgentHeartbeatRequest buildHeartbeat() {
        List<SourceTool> active = collectors.stream()
                .filter(SessionCollector::isEnabled)
                .map(SessionCollector::sourceTool)
                .toList();

        var user = userInfoResolver.resolve();
        List<CollectorDiscoveryEntry> discoveryResults = discoveryService.lastResults().stream()
                .map(result -> new CollectorDiscoveryEntry(
                        result.configType(),
                        CollectorDiscoveryStatus.valueOf(result.status().name()),
                        result.watchPath() == null ? null : result.watchPath().toString(),
                        result.detail(),
                        result.newestLogAt()
                ))
                .toList();
        List<ParseErrorEntry> parseErrors = parseHealthTracker.snapshot().entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> new ParseErrorEntry(entry.getKey(), entry.getValue()))
                .toList();

        return AgentHeartbeatRequest.withHealth(
                hostname(),
                user.developerEmail(),
                user.osUsername(),
                user.displayName(),
                user.gitEmail(),
                active,
                eventBuffer.queueDepth(),
                Instant.now(),
                properties.getPlatform().isAutoDiscovery(),
                discoveryResults.isEmpty() ? null : discoveryResults,
                parseErrors.isEmpty() ? null : parseErrors,
                eventBuffer.droppedTotal() > 0 ? eventBuffer.droppedTotal() : null
        );
    }

    private String hostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return "unknown-host";
        }
    }
}
