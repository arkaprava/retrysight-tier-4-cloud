/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.ingest;

import com.agentretry.contract.AgentHeartbeatRequest;
import com.agentretry.contract.IngestBatchRequest;
import com.agentretry.contract.IngestBatchResponse;
import com.agentretry.manager.domain.AgentEntity;
import com.agentretry.manager.service.IngestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ingest")
public class IngestController {

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeat(
            @RequestHeader("X-Agent-Api-Key") String apiKey,
            @RequestHeader(value = "X-Agent-Id", required = false) UUID agentId,
            @Valid @RequestBody AgentHeartbeatRequest request
    ) {
        AgentEntity agent = ingestService.registerOrResolveAgent(apiKey, agentId, request);
        ingestService.processBatch(agent.getId(), new IngestBatchRequest(request, null, null));
        return ResponseEntity.ok(Map.of(
                "agentId", agent.getId(),
                "status", "ok"
        ));
    }

    @PostMapping("/batch")
    public ResponseEntity<IngestBatchResponse> batch(
            @RequestHeader("X-Agent-Api-Key") String apiKey,
            @RequestHeader(value = "X-Agent-Id", required = false) UUID agentId,
            @Valid @RequestBody IngestBatchRequest request
    ) {
        AgentHeartbeatRequest heartbeat = request.heartbeat();
        if (agentId == null && heartbeat == null) {
            throw new IllegalArgumentException("Either X-Agent-Id or heartbeat payload is required");
        }

        AgentEntity agent = ingestService.registerOrResolveAgent(apiKey, agentId, heartbeat);
        IngestBatchResponse response = ingestService.processBatch(agent.getId(), request);
        return ResponseEntity.ok(response);
    }
}
