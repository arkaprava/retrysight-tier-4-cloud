/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.otel;

import com.agentretry.contract.SourceTool;
import com.agentretry.manager.domain.SpanEntity;
import com.agentretry.manager.domain.TaskEntity;
import com.agentretry.manager.repository.TaskRepository;
import com.agentretry.manager.trace.SpanService;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class OtelTraceMapper {
    private final TaskRepository taskRepository;
    private final SpanService spanService;

    public OtelTraceMapper(TaskRepository taskRepository, SpanService spanService) {
        this.taskRepository = taskRepository;
        this.spanService = spanService;
    }

    public List<SpanEntity> mapPayload(Map<String, Object> body) {
        if (body == null || !body.containsKey("resourceSpans")) {
            return List.of();
        }
        Object resourceSpans = body.get("resourceSpans");
        if (!(resourceSpans instanceof List<?> groups)) {
            return List.of();
        }

        List<SpanEntity> spans = new ArrayList<>();
        Map<String, UUID> externalToInternal = new HashMap<>();
        Map<String, String> externalParent = new HashMap<>();

        for (Object groupObj : groups) {
            if (!(groupObj instanceof Map<?, ?> group)) {
                continue;
            }
            Map<String, Object> resourceAttrs = attributes(group.get("resource"));
            UUID taskId = resolveTaskId(resourceAttrs);
            String sourceTool = stringAttr(resourceAttrs, "source.tool", "source_tool");

            Object scopeSpans = group.get("scopeSpans");
            if (!(scopeSpans instanceof List<?> scopes)) {
                continue;
            }
            for (Object scopeObj : scopes) {
                if (!(scopeObj instanceof Map<?, ?> scope)) {
                    continue;
                }
                Object spanList = scope.get("spans");
                if (!(spanList instanceof List<?> otelSpans)) {
                    continue;
                }
                for (Object spanObj : otelSpans) {
                    if (!(spanObj instanceof Map<?, ?> otelSpan)) {
                        continue;
                    }
                    String traceId = stringValue(otelSpan.get("traceId"));
                    String spanId = stringValue(otelSpan.get("spanId"));
                    if (spanId == null || spanId.isBlank()) {
                        continue;
                    }
                    UUID resolvedTaskId = taskId != null ? taskId : resolveTaskId(attributes(otelSpan));
                    if (resolvedTaskId == null) {
                        continue;
                    }

                    String parentExternal = stringValue(otelSpan.get("parentSpanId"));
                    if (parentExternal != null && !parentExternal.isBlank()) {
                        externalParent.put(spanId, parentExternal);
                    }

                    Instant start = nanoToInstant(otelSpan.get("startTimeUnixNano"));
                    Instant end = nanoToInstant(otelSpan.get("endTimeUnixNano"));
                    long durationMs = Math.max(1, end.toEpochMilli() - start.toEpochMilli());

                    Map<String, Object> attrs = attributes(otelSpan);
                    String input = stringAttr(attrs, "input", "gen_ai.prompt", "prompt");
                    String output = stringAttr(attrs, "output", "gen_ai.completion", "completion");

                    SpanEntity entity = spanService.buildSpan(
                            resolvedTaskId,
                            traceId,
                            spanId,
                            null,
                            stringValue(otelSpan.get("name")),
                            spanKind(otelSpan.get("kind")),
                            start,
                            durationMs,
                            spanStatus(otelSpan.get("status")),
                            attrs,
                            input,
                            output,
                            sourceTool
                    );
                    externalToInternal.put(spanId, entity.getId());
                    spans.add(entity);
                }
            }
        }

        for (SpanEntity span : spans) {
            String parentExternal = externalParent.get(span.getExternalSpanId());
            if (parentExternal != null) {
                UUID parentId = externalToInternal.get(parentExternal);
                span.setParentSpanId(parentId);
            }
        }
        return spans;
    }

    private UUID resolveTaskId(Map<String, Object> attrs) {
        String taskIdText = stringAttr(attrs, "task.id", "task_id", "retrysight.task_id");
        if (taskIdText != null) {
            try {
                return UUID.fromString(taskIdText.trim());
            } catch (IllegalArgumentException ignored) {
            }
        }
        String sessionId = stringAttr(attrs, "session.id", "session_id", "retrysight.session_id");
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        Optional<TaskEntity> bySession = taskRepository.findBySourceToolAndExternalSessionId(
                SourceTool.CURSOR, sessionId);
        if (bySession.isPresent()) {
            return bySession.get().getId();
        }
        for (SourceTool tool : SourceTool.values()) {
            Optional<TaskEntity> match = taskRepository.findBySourceToolAndExternalSessionId(tool, sessionId);
            if (match.isPresent()) {
                return match.get().getId();
            }
        }
        return uuidFromSession(sessionId);
    }

    private static UUID uuidFromSession(String sessionId) {
        return UUID.nameUUIDFromBytes(("retrysight-session:" + sessionId).getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> attributes(Object container) {
        if (!(container instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Object attrs = map.get("attributes");
        if (!(attrs instanceof List<?> list)) {
            return Map.of();
        }
        Map<String, Object> result = new HashMap<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> attr)) {
                continue;
            }
            String key = stringValue(attr.get("key"));
            if (key == null) {
                continue;
            }
            Object valueObj = attr.get("value");
            if (valueObj instanceof Map<?, ?> valueMap) {
                if (valueMap.containsKey("stringValue")) {
                    result.put(key, stringValue(valueMap.get("stringValue")));
                } else if (valueMap.containsKey("intValue")) {
                    result.put(key, stringValue(valueMap.get("intValue")));
                } else if (valueMap.containsKey("boolValue")) {
                    result.put(key, String.valueOf(valueMap.get("boolValue")));
                }
            }
        }
        return result;
    }

    private static String stringAttr(Map<String, Object> attrs, String... keys) {
        for (String key : keys) {
            Object value = attrs.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private static Instant nanoToInstant(Object nanoValue) {
        if (nanoValue == null) {
            return Instant.now();
        }
        try {
            long nano = Long.parseLong(String.valueOf(nanoValue));
            return Instant.ofEpochSecond(nano / 1_000_000_000L, nano % 1_000_000_000L);
        } catch (NumberFormatException ex) {
            return Instant.now();
        }
    }

    private static String spanKind(Object kind) {
        if (kind == null) {
            return "SPAN";
        }
        return switch (String.valueOf(kind)) {
            case "1" -> "INTERNAL";
            case "2" -> "SERVER";
            case "3" -> "CLIENT";
            case "4" -> "PRODUCER";
            case "5" -> "CONSUMER";
            default -> String.valueOf(kind).toUpperCase(Locale.ROOT);
        };
    }

    @SuppressWarnings("unchecked")
    private static String spanStatus(Object statusObj) {
        if (!(statusObj instanceof Map<?, ?> status)) {
            return "ok";
        }
        Object code = status.get("code");
        if (code == null) {
            return "ok";
        }
        return "2".equals(String.valueOf(code)) || "STATUS_CODE_ERROR".equals(String.valueOf(code))
                ? "error" : "ok";
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
