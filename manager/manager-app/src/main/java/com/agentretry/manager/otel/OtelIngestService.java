/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.otel;

import com.agentretry.contract.AgentHeartbeatRequest;
import com.agentretry.contract.IngestBatchItem;
import com.agentretry.contract.IngestBatchRequest;
import com.agentretry.contract.IngestBatchResponse;
import com.agentretry.contract.SourceTool;
import com.agentretry.manager.config.OtelProperties;
import com.agentretry.manager.domain.AgentEntity;
import com.agentretry.manager.domain.SpanEntity;
import com.agentretry.manager.service.IngestService;
import com.agentretry.manager.trace.SpanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class OtelIngestService {
    private static final Logger log = LoggerFactory.getLogger(OtelIngestService.class);

    private final OtelProperties otelProperties;
    private final CoworkOtelMapper coworkOtelMapper;
    private final OtelTraceMapper otelTraceMapper;
    private final SpanService spanService;
    private final IngestService ingestService;

    public OtelIngestService(
            OtelProperties otelProperties,
            CoworkOtelMapper coworkOtelMapper,
            OtelTraceMapper otelTraceMapper,
            SpanService spanService,
            IngestService ingestService
    ) {
        this.otelProperties = otelProperties;
        this.coworkOtelMapper = coworkOtelMapper;
        this.otelTraceMapper = otelTraceMapper;
        this.spanService = spanService;
        this.ingestService = ingestService;
    }

    public Map<String, Object> ingestLogs(String apiKey, Map<String, Object> body) {
        requireEnabled();
        if (!otelProperties.isCoworkMappingEnabled()) {
            return Map.of(
                    "status", "accepted",
                    "signal", "logs",
                    "mappedItems", 0,
                    "persisted", false,
                    "reason", "cowork mapping disabled"
            );
        }
        List<IngestBatchItem> items = coworkOtelMapper.mapPayload(body);
        if (items.isEmpty()) {
            log.info("OTel logs accepted but produced 0 ingest items");
            return Map.of(
                    "status", "accepted",
                    "signal", "logs",
                    "mappedItems", 0,
                    "accepted", 0,
                    "skipped", 0,
                    "errors", 0
            );
        }

        String email = coworkOtelMapper.resolveDeveloperEmail(body);
        String hostname = coworkOtelMapper.resolveHostname(body);
        AgentHeartbeatRequest heartbeat = AgentHeartbeatRequest.withUserInfo(
                hostname,
                email,
                null,
                "Claude Cowork",
                email,
                List.of(SourceTool.CLAUDE_COWORK),
                items.size(),
                Instant.now()
        );
        AgentEntity agent = ingestService.registerOrResolveAgent(apiKey, heartbeat);
        IngestBatchResponse response = ingestService.processBatch(
                agent.getId(),
                new IngestBatchRequest(heartbeat, null, items)
        );
        log.info(
                "OTel Cowork ingest agent={} mapped={} accepted={} skipped={} errors={}",
                agent.getId(), items.size(), response.accepted(), response.skipped(), response.errors()
        );
        return Map.of(
                "status", "accepted",
                "signal", "logs",
                "sourceTool", SourceTool.CLAUDE_COWORK.name(),
                "agentId", agent.getId().toString(),
                "mappedItems", items.size(),
                "accepted", response.accepted(),
                "skipped", response.skipped(),
                "errors", response.errors()
        );
    }

    public Map<String, Object> ingestMetrics(Map<String, Object> body) {
        requireEnabled();
        // Metrics are accepted for forward-compat; Cowork analytics use logs/events.
        int groups = 0;
        if (body != null && body.get("resourceMetrics") instanceof List<?> list) {
            groups = list.size();
        }
        return Map.of("status", "accepted", "signal", "metrics", "resourceGroups", groups, "persisted", false);
    }

    public Map<String, Object> ingestTraces(Map<String, Object> body) {
        requireEnabled();
        if (!otelProperties.isTraceMappingEnabled()) {
            return Map.of(
                    "status", "accepted",
                    "signal", "traces",
                    "mappedSpans", 0,
                    "persisted", false,
                    "reason", "trace mapping disabled"
            );
        }
        List<SpanEntity> spans = otelTraceMapper.mapPayload(body);
        int saved = spanService.persistSpans(spans);
        log.info("OTel trace ingest mapped={} saved={}", spans.size(), saved);
        return Map.of(
                "status", "accepted",
                "signal", "traces",
                "mappedSpans", spans.size(),
                "savedSpans", saved
        );
    }

    private void requireEnabled() {
        if (!otelProperties.isIngestEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "OTel ingest is disabled");
        }
    }
}
