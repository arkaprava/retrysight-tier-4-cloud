/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.ingest;

import com.agentretry.contract.IngestBatchRequest;
import com.agentretry.contract.IngestBatchResponse;
import com.agentretry.manager.domain.AgentEntity;
import com.agentretry.manager.platform.FeatureGateService;
import com.agentretry.manager.service.IngestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ingest")
public class PushIngestController {

    private final FeatureGateService featureGateService;
    private final IngestService ingestService;

    public PushIngestController(FeatureGateService featureGateService, IngestService ingestService) {
        this.featureGateService = featureGateService;
        this.ingestService = ingestService;
    }

    @PostMapping("/events")
    public ResponseEntity<?> ingestEvents(
            @RequestHeader("X-Agent-Api-Key") String apiKey,
            @RequestHeader(value = "X-Agent-Id", required = false) UUID agentId,
            @Valid @RequestBody IngestBatchRequest request
    ) {
        if (!featureGateService.isPushIngestEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Push ingest is not enabled for this installation");
        }

        if (agentId == null && request.heartbeat() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Either X-Agent-Id or heartbeat payload is required"
            );
        }

        AgentEntity agent = ingestService.registerOrResolveAgent(apiKey, agentId, request.heartbeat());
        IngestBatchResponse response = ingestService.processBatch(agent.getId(), request);
        return ResponseEntity.accepted().body(Map.of(
                "status", "accepted",
                "agentId", agent.getId(),
                "accepted", response.accepted(),
                "skipped", response.skipped(),
                "errors", response.errors()
        ));
    }
}
