/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.otel;

import com.agentretry.contract.IngestBatchItem;
import com.agentretry.contract.IngestItemType;
import com.agentretry.contract.SourceTool;
import com.agentretry.contract.TaskEventRequest;
import com.agentretry.contract.TaskEventType;
import com.agentretry.contract.TaskStartRequest;
import com.agentretry.contract.TokenUsageRequest;
import com.agentretry.manager.config.PlatformProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoworkOtelMapperTest {

    private CoworkOtelMapper mapper;

    @BeforeEach
    void setUp() {
        PlatformProperties props = new PlatformProperties();
        props.getPrivacy().setStoreFullPrompts(false);
        mapper = new CoworkOtelMapper(props);
    }

    @Test
    void mapsFlatCoworkEventsToIngestItems() {
        Map<String, Object> prompt = new HashMap<>();
        prompt.put("event.name", "user_prompt");
        prompt.put("service.name", "cowork");
        prompt.put("session.id", "sess-1");
        prompt.put("prompt.id", "p-1");
        prompt.put("user.email", "dev@example.com");
        prompt.put("event.sequence", 1);
        prompt.put("prompt", "secret prompt text");
        prompt.put("workspace.host_paths", List.of("/Users/dev/project"));

        Map<String, Object> apiRequest = new HashMap<>();
        apiRequest.put("event.name", "api_request");
        apiRequest.put("service.name", "cowork");
        apiRequest.put("session.id", "sess-1");
        apiRequest.put("prompt.id", "p-1");
        apiRequest.put("event.sequence", 2);
        apiRequest.put("model", "claude-sonnet-5");
        apiRequest.put("input_tokens", 100);
        apiRequest.put("output_tokens", 50);
        apiRequest.put("cache_read_tokens", 10);
        apiRequest.put("cache_creation_tokens", 5);
        apiRequest.put("cost_usd", 0.012);

        Map<String, Object> toolResult = new HashMap<>();
        toolResult.put("event.name", "tool_result");
        toolResult.put("service.name", "cowork");
        toolResult.put("session.id", "sess-1");
        toolResult.put("prompt.id", "p-1");
        toolResult.put("event.sequence", 3);
        toolResult.put("tool_name", "Edit");
        toolResult.put("success", "true");
        toolResult.put("decision_type", "accept");
        toolResult.put("duration_ms", 120);

        Map<String, Object> apiError = new HashMap<>();
        apiError.put("event.name", "api_error");
        apiError.put("service.name", "cowork");
        apiError.put("session.id", "sess-1");
        apiError.put("prompt.id", "p-1");
        apiError.put("event.sequence", 4);
        apiError.put("error", "rate limited");
        apiError.put("status_code", "429");
        apiError.put("attempt", 2);

        Map<String, Object> body = Map.of("events", List.of(prompt, apiRequest, toolResult, apiError));

        List<IngestBatchItem> items = mapper.mapPayload(body);
        assertFalse(items.isEmpty());

        IngestBatchItem start = items.stream()
                .filter(i -> i.type() == IngestItemType.TASK_START)
                .findFirst()
                .orElseThrow();
        TaskStartRequest startPayload = (TaskStartRequest) start.payload();
        assertEquals(SourceTool.CLAUDE_COWORK, startPayload.sourceTool());
        assertEquals("sess-1", startPayload.externalSessionId());
        assertEquals("/Users/dev/project", startPayload.metadata().get("projectRoot"));

        assertTrue(items.stream().anyMatch(i -> i.type() == IngestItemType.TOKEN_USAGE));
        TokenUsageRequest token = (TokenUsageRequest) items.stream()
                .filter(i -> i.type() == IngestItemType.TOKEN_USAGE)
                .findFirst()
                .orElseThrow()
                .payload();
        assertEquals(100L, token.inputTokens());
        assertEquals(50L, token.outputTokens());
        assertEquals(0.012, token.estimatedCostUsd());

        TaskEventRequest error = (TaskEventRequest) items.stream()
                .filter(i -> i.type() == IngestItemType.TASK_EVENT)
                .map(i -> (TaskEventRequest) i.payload())
                .filter(e -> e.eventType() == TaskEventType.TEST_FAIL)
                .findFirst()
                .orElseThrow();
        assertTrue(error.retry());

        // Privacy: prompt body not stored by default
        TaskEventRequest promptEvent = (TaskEventRequest) items.stream()
                .filter(i -> i.type() == IngestItemType.TASK_EVENT)
                .map(i -> (TaskEventRequest) i.payload())
                .filter(e -> e.eventType() == TaskEventType.SESSION_START)
                .findFirst()
                .orElseThrow();
        assertFalse(promptEvent.payload().containsKey("prompt"));
    }

    @Test
    void mapsOtlpResourceLogsShape() {
        Map<String, Object> body = Map.of(
                "resourceLogs", List.of(
                        Map.of(
                                "resource", Map.of(
                                        "attributes", List.of(
                                                Map.of("key", "service.name", "value", Map.of("stringValue", "cowork"))
                                        )
                                ),
                                "scopeLogs", List.of(
                                        Map.of(
                                                "logRecords", List.of(
                                                        Map.of(
                                                                "timeUnixNano", "1700000000000000000",
                                                                "attributes", List.of(
                                                                        Map.of("key", "event.name", "value", Map.of("stringValue", "user_prompt")),
                                                                        Map.of("key", "session.id", "value", Map.of("stringValue", "otel-sess")),
                                                                        Map.of("key", "prompt.id", "value", Map.of("stringValue", "otel-p")),
                                                                        Map.of("key", "event.sequence", "value", Map.of("intValue", 1)),
                                                                        Map.of("key", "user.email", "value", Map.of("stringValue", "a@b.com"))
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );

        List<IngestBatchItem> items = mapper.mapPayload(body);
        assertEquals(2, items.size()); // TASK_START + SESSION_START
        assertEquals("a@b.com", mapper.resolveDeveloperEmail(body));
    }
}
