/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.otel;

import com.agentretry.manager.config.OtelProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * OTLP/HTTP JSON ingest. Claude Cowork logs are mapped into the standard ingest pipeline.
 *
 * <p>Configure Cowork Admin → OTLP endpoint:
 * {@code http(s)://&lt;manager&gt;:28477/api/v1/otel/v1/logs}
 * with header {@code X-Agent-Api-Key: &lt;agent-key&gt;} (or {@code Authorization: Bearer &lt;key&gt;}).
 */
@RestController
@RequestMapping("/api/v1/otel")
public class OtelIngestController {

    private final OtelIngestService otelIngestService;
    private final OtelProperties otelProperties;
    private final ObjectMapper objectMapper;

    public OtelIngestController(
            OtelIngestService otelIngestService,
            OtelProperties otelProperties,
            ObjectMapper objectMapper
    ) {
        this.otelIngestService = otelIngestService;
        this.otelProperties = otelProperties;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/v1/metrics")
    public ResponseEntity<Map<String, Object>> ingestMetrics(@RequestBody(required = false) Map<String, Object> body) {
        validatePayloadSize(body);
        return ResponseEntity.accepted().body(otelIngestService.ingestMetrics(body));
    }

    @PostMapping({"/v1/logs", "/v1/logs/"})
    public ResponseEntity<Map<String, Object>> ingestLogs(
            @RequestHeader(value = "X-Agent-Api-Key", required = false) String apiKeyHeader,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        validatePayloadSize(body);
        validateLogRecordCount(body);
        String apiKey = resolveApiKey(apiKeyHeader, authorization);
        return ResponseEntity.accepted().body(otelIngestService.ingestLogs(apiKey, body));
    }

    @PostMapping({"/v1/traces", "/v1/traces/"})
    public ResponseEntity<Map<String, Object>> ingestTraces(
            @RequestBody(required = false) Map<String, Object> body
    ) {
        validatePayloadSize(body);
        validateSpanCount(body);
        return ResponseEntity.accepted().body(otelIngestService.ingestTraces(body));
    }

    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingest(
            @RequestHeader(value = "X-Agent-Api-Key", required = false) String apiKeyHeader,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        if (body != null && body.containsKey("resourceSpans")) {
            return ingestTraces(body);
        }
        if (body != null && (body.containsKey("resourceLogs") || body.containsKey("events"))) {
            return ingestLogs(apiKeyHeader, authorization, body);
        }
        return ingestMetrics(body);
    }

    private void validatePayloadSize(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return;
        }
        try {
            int size = objectMapper.writeValueAsBytes(body).length;
            if (size > otelProperties.getMaxRequestBodyBytes()) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "OTLP payload too large");
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OTLP payload");
        }
    }

    @SuppressWarnings("unchecked")
    private void validateLogRecordCount(Map<String, Object> body) {
        if (body == null) {
            return;
        }
        int count = 0;
        Object resourceLogs = body.get("resourceLogs");
        if (resourceLogs instanceof List<?> resources) {
            for (Object resource : resources) {
                if (resource instanceof Map<?, ?> resourceMap) {
                    Object scopeLogs = resourceMap.get("scopeLogs");
                    if (scopeLogs instanceof List<?> scopes) {
                        for (Object scope : scopes) {
                            if (scope instanceof Map<?, ?> scopeMap) {
                                Object logRecords = scopeMap.get("logRecords");
                                if (logRecords instanceof List<?> records) {
                                    count += records.size();
                                }
                            }
                        }
                    }
                }
            }
        }
        if (count > otelProperties.getMaxLogRecords()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many log records in OTLP payload");
        }
    }

    @SuppressWarnings("unchecked")
    private void validateSpanCount(Map<String, Object> body) {
        if (body == null) {
            return;
        }
        int count = 0;
        Object resourceSpans = body.get("resourceSpans");
        if (resourceSpans instanceof List<?> resources) {
            for (Object resource : resources) {
                if (resource instanceof Map<?, ?> resourceMap) {
                    Object scopeSpans = resourceMap.get("scopeSpans");
                    if (scopeSpans instanceof List<?> scopes) {
                        for (Object scope : scopes) {
                            if (scope instanceof Map<?, ?> scopeMap) {
                                Object spans = scopeMap.get("spans");
                                if (spans instanceof List<?> spanList) {
                                    count += spanList.size();
                                }
                            }
                        }
                    }
                }
            }
        }
        if (count > otelProperties.getMaxSpans()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many spans in OTLP payload");
        }
    }

    private static String resolveApiKey(String apiKeyHeader, String authorization) {
        if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
            return apiKeyHeader.trim();
        }
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing X-Agent-Api-Key or Authorization Bearer token");
    }
}
