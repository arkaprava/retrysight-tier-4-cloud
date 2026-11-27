/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.otel;

import com.agentretry.contract.IngestBatchItem;
import com.agentretry.contract.IngestItemType;
import com.agentretry.contract.ProjectContextKeys;
import com.agentretry.contract.SourceTool;
import com.agentretry.contract.TaskEventRequest;
import com.agentretry.contract.TaskEventType;
import com.agentretry.contract.TaskStartRequest;
import com.agentretry.contract.TokenUsageRequest;
import com.agentretry.manager.config.PlatformProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Maps Claude Cowork OTLP log/event records into ingest batch items.
 *
 * @see <a href="https://claude.com/docs/cowork/monitoring">Cowork monitoring</a>
 */
@Component
public class CoworkOtelMapper {

    private final PlatformProperties platformProperties;

    public CoworkOtelMapper(PlatformProperties platformProperties) {
        this.platformProperties = platformProperties;
    }

    public List<IngestBatchItem> mapPayload(Map<String, Object> body) {
        List<OtelLogRecord> records = extractRecords(body);
        records.sort(Comparator
                .comparing(OtelLogRecord::sessionId, Comparator.nullsLast(String::compareTo))
                .thenComparingInt(r -> r.sequence() == null ? 0 : r.sequence())
                .thenComparing(OtelLogRecord::timestamp, Comparator.nullsLast(Instant::compareTo)));

        List<IngestBatchItem> items = new ArrayList<>();
        Set<String> startedSessions = new HashSet<>();

        for (OtelLogRecord rec : records) {
            if (rec.sessionId() == null || rec.sessionId().isBlank()) {
                continue;
            }
            if (!isCowork(rec)) {
                continue;
            }
            UUID taskId = taskIdForSession(rec.sessionId());
            Instant at = rec.timestamp() != null ? rec.timestamp() : Instant.now();
            int seq = rec.sequence() != null ? rec.sequence() : 0;

            if (startedSessions.add(rec.sessionId())) {
                items.add(taskStart(taskId, rec, at));
            }

            switch (rec.eventName()) {
                case "user_prompt" -> items.add(promptEvent(taskId, rec, at, seq));
                case "api_request" -> items.add(tokenUsage(taskId, rec, at, seq));
                case "tool_result" -> items.add(toolResultEvent(taskId, rec, at, seq));
                case "tool_decision" -> items.add(toolDecisionEvent(taskId, rec, at, seq));
                case "api_error" -> items.add(apiErrorEvent(taskId, rec, at, seq));
                case "assistant_response" -> {
                    if (storeFullPrompts()) {
                        items.add(assistantEvent(taskId, rec, at, seq));
                    }
                }
                default -> {
                    // Unknown Cowork event — keep a lightweight breadcrumb when named.
                    if (rec.eventName() != null && !rec.eventName().isBlank()) {
                        items.add(genericEvent(taskId, rec, at, seq));
                    }
                }
            }
        }
        return items;
    }

    /** Resolve developer email from the first Cowork record (for agent heartbeat). */
    public String resolveDeveloperEmail(Map<String, Object> body) {
        for (OtelLogRecord rec : extractRecords(body)) {
            if (rec.userEmail() != null && !rec.userEmail().isBlank()) {
                return rec.userEmail().trim();
            }
        }
        return "cowork@unknown";
    }

    public String resolveHostname(Map<String, Object> body) {
        for (OtelLogRecord rec : extractRecords(body)) {
            if (rec.organizationId() != null && !rec.organizationId().isBlank()) {
                return "cowork-" + rec.organizationId().substring(0, Math.min(8, rec.organizationId().length()));
            }
        }
        return "cowork-otel";
    }

    static UUID taskIdForSession(String sessionId) {
        return UUID.nameUUIDFromBytes(("cowork-session:" + sessionId).getBytes(StandardCharsets.UTF_8));
    }

    private IngestBatchItem taskStart(UUID taskId, OtelLogRecord rec, Instant at) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("ingestChannel", "otel");
        metadata.put("serviceName", rec.serviceName());
        metadata.put("sessionId", rec.sessionId());
        if (rec.organizationId() != null) {
            metadata.put("organizationId", rec.organizationId());
        }
        if (rec.userAccountId() != null) {
            metadata.put("userAccountId", rec.userAccountId());
        }
        List<String> hostPaths = rec.workspaceHostPaths();
        if (hostPaths != null && !hostPaths.isEmpty()) {
            metadata.put(ProjectContextKeys.PROJECT_ROOT, hostPaths.get(0));
            metadata.put("workspaceHostPaths", hostPaths);
        }
        String description = "Claude Cowork session " + shortId(rec.sessionId());
        TaskStartRequest start = new TaskStartRequest(
                taskId,
                rec.sessionId(),
                SourceTool.CLAUDE_COWORK,
                description,
                at,
                metadata
        );
        return new IngestBatchItem("cowork:start:" + rec.sessionId(), IngestItemType.TASK_START, start);
    }

    private IngestBatchItem promptEvent(UUID taskId, OtelLogRecord rec, Instant at, int seq) {
        Map<String, Object> payload = basePayload(rec);
        payload.put("promptId", rec.promptId());
        payload.put("promptLength", rec.attrLong("prompt_length"));
        if (storeFullPrompts()) {
            String prompt = rec.attrString("prompt");
            if (prompt != null) {
                payload.put("prompt", prompt);
            }
        }
        TaskEventRequest event = new TaskEventRequest(
                taskId, TaskEventType.SESSION_START, at, false, seq, payload
        );
        return new IngestBatchItem(idem(rec, "user_prompt"), IngestItemType.TASK_EVENT, event);
    }

    private IngestBatchItem tokenUsage(UUID taskId, OtelLogRecord rec, Instant at, int seq) {
        TokenUsageRequest usage = new TokenUsageRequest(
                taskId,
                Math.max(seq, 0),
                rec.attrString("model"),
                rec.attrLong("input_tokens"),
                rec.attrLong("output_tokens"),
                rec.attrLong("cache_read_tokens"),
                rec.attrLong("cache_creation_tokens"),
                0L,
                rec.attrDouble("cost_usd"),
                at
        );
        return new IngestBatchItem(idem(rec, "api_request"), IngestItemType.TOKEN_USAGE, usage);
    }

    private IngestBatchItem toolResultEvent(UUID taskId, OtelLogRecord rec, Instant at, int seq) {
        boolean success = "true".equalsIgnoreCase(String.valueOf(rec.attrString("success")));
        String decision = rec.attrString("decision_type");
        boolean rejected = "reject".equalsIgnoreCase(decision);
        TaskEventType type = rejected
                ? TaskEventType.DIFF_REJECTED
                : (success ? TaskEventType.EDIT : TaskEventType.TEST_FAIL);
        Map<String, Object> payload = basePayload(rec);
        payload.put("toolName", rec.attrString("tool_name"));
        payload.put("success", success);
        payload.put("durationMs", rec.attrLong("duration_ms"));
        payload.put("decisionType", decision);
        payload.put("decisionSource", rec.attrString("decision_source"));
        payload.put("error", rec.attrString("error"));
        if (storeFullPrompts()) {
            payload.put("toolParameters", rec.attrString("tool_parameters"));
            payload.put("toolInput", rec.attrString("tool_input"));
        }
        TaskEventRequest event = new TaskEventRequest(taskId, type, at, rejected || !success, seq, payload);
        return new IngestBatchItem(idem(rec, "tool_result"), IngestItemType.TASK_EVENT, event);
    }

    private IngestBatchItem toolDecisionEvent(UUID taskId, OtelLogRecord rec, Instant at, int seq) {
        String decision = rec.attrString("decision");
        boolean rejected = "reject".equalsIgnoreCase(decision);
        TaskEventType type = rejected ? TaskEventType.DIFF_REJECTED : TaskEventType.DIFF_ACCEPTED;
        Map<String, Object> payload = basePayload(rec);
        payload.put("toolName", rec.attrString("tool_name"));
        payload.put("decision", decision);
        payload.put("source", rec.attrString("source"));
        TaskEventRequest event = new TaskEventRequest(taskId, type, at, rejected, seq, payload);
        return new IngestBatchItem(idem(rec, "tool_decision"), IngestItemType.TASK_EVENT, event);
    }

    private IngestBatchItem apiErrorEvent(UUID taskId, OtelLogRecord rec, Instant at, int seq) {
        Map<String, Object> payload = basePayload(rec);
        payload.put("model", rec.attrString("model"));
        payload.put("error", rec.attrString("error"));
        payload.put("statusCode", rec.attrString("status_code"));
        payload.put("attempt", rec.attrLong("attempt"));
        TaskEventRequest event = new TaskEventRequest(
                taskId, TaskEventType.TEST_FAIL, at, true, seq, payload
        );
        return new IngestBatchItem(idem(rec, "api_error"), IngestItemType.TASK_EVENT, event);
    }

    private IngestBatchItem assistantEvent(UUID taskId, OtelLogRecord rec, Instant at, int seq) {
        Map<String, Object> payload = basePayload(rec);
        payload.put("model", rec.attrString("model"));
        payload.put("responseLength", rec.attrLong("response_length"));
        payload.put("response", rec.attrString("response"));
        TaskEventRequest event = new TaskEventRequest(
                taskId, TaskEventType.COMPACTION, at, false, seq, payload
        );
        return new IngestBatchItem(idem(rec, "assistant_response"), IngestItemType.TASK_EVENT, event);
    }

    private IngestBatchItem genericEvent(UUID taskId, OtelLogRecord rec, Instant at, int seq) {
        Map<String, Object> payload = basePayload(rec);
        payload.put("eventName", rec.eventName());
        TaskEventRequest event = new TaskEventRequest(
                taskId, TaskEventType.SUBAGENT, at, false, seq, payload
        );
        return new IngestBatchItem(idem(rec, rec.eventName()), IngestItemType.TASK_EVENT, event);
    }

    private Map<String, Object> basePayload(OtelLogRecord rec) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("promptId", rec.promptId());
        payload.put("sessionId", rec.sessionId());
        return payload;
    }

    private String idem(OtelLogRecord rec, String kind) {
        return "cowork:" + kind + ":" + rec.sessionId() + ":"
                + (rec.sequence() != null ? rec.sequence() : "0") + ":"
                + (rec.promptId() != null ? rec.promptId() : "-");
    }

    private boolean storeFullPrompts() {
        return platformProperties.getPrivacy() != null
                && platformProperties.getPrivacy().isStoreFullPrompts();
    }

    private static boolean isCowork(OtelLogRecord rec) {
        String service = rec.serviceName();
        if (service != null && service.toLowerCase(Locale.ROOT).contains("cowork")) {
            return true;
        }
        // Attribute-only payloads without resource service.name still accepted when event is known.
        return rec.eventName() != null && Set.of(
                "user_prompt", "api_request", "tool_result", "tool_decision", "api_error", "assistant_response"
        ).contains(rec.eventName());
    }

    private static String shortId(String id) {
        if (id == null) return "";
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    @SuppressWarnings("unchecked")
    List<OtelLogRecord> extractRecords(Map<String, Object> body) {
        List<OtelLogRecord> out = new ArrayList<>();
        if (body == null) {
            return out;
        }
        // OTLP/HTTP JSON: resourceLogs[].scopeLogs[].logRecords[]
        Object resourceLogs = body.get("resourceLogs");
        if (resourceLogs instanceof List<?> rl) {
            for (Object resourceLog : rl) {
                if (!(resourceLog instanceof Map<?, ?> rlMap)) continue;
                Map<String, Object> resourceAttrs = flattenAttributes(
                        ((Map<String, Object>) rlMap).get("resource") instanceof Map<?, ?> res
                                ? ((Map<String, Object>) res).get("attributes")
                                : null
                );
                String serviceName = firstNonBlank(
                        stringAttr(resourceAttrs, "service.name"),
                        stringAttr(resourceAttrs, "service_name")
                );
                Object scopeLogs = ((Map<String, Object>) rlMap).get("scopeLogs");
                if (!(scopeLogs instanceof List<?> sl)) continue;
                for (Object scopeLog : sl) {
                    if (!(scopeLog instanceof Map<?, ?>)) continue;
                    Object logRecords = ((Map<String, Object>) scopeLog).get("logRecords");
                    if (!(logRecords instanceof List<?> records)) continue;
                    for (Object lr : records) {
                        if (lr instanceof Map<?, ?> map) {
                            out.add(OtelLogRecord.from((Map<String, Object>) map, resourceAttrs, serviceName));
                        }
                    }
                }
            }
        }
        // Convenience: { "events": [ { ...cowork attrs } ] }
        Object events = body.get("events");
        if (events instanceof List<?> list) {
            for (Object e : list) {
                if (e instanceof Map<?, ?> map) {
                    out.add(OtelLogRecord.fromFlat((Map<String, Object>) map));
                }
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> flattenAttributes(Object attributes) {
        Map<String, Object> flat = new HashMap<>();
        if (!(attributes instanceof List<?> list)) {
            if (attributes instanceof Map<?, ?> map) {
                map.forEach((k, v) -> flat.put(String.valueOf(k), v));
            }
            return flat;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> attr)) continue;
            Object key = attr.get("key");
            if (key == null) continue;
            Object value = attr.get("value");
            flat.put(String.valueOf(key), unwrapOtelValue(value));
        }
        return flat;
    }

    @SuppressWarnings("unchecked")
    static Object unwrapOtelValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return value;
        }
        Map<String, Object> m = (Map<String, Object>) map;
        if (m.containsKey("stringValue")) return m.get("stringValue");
        if (m.containsKey("intValue")) return m.get("intValue");
        if (m.containsKey("doubleValue")) return m.get("doubleValue");
        if (m.containsKey("boolValue")) return m.get("boolValue");
        if (m.get("arrayValue") instanceof Map<?, ?> arr
                && ((Map<String, Object>) arr).get("values") instanceof List<?> values) {
            List<Object> out = new ArrayList<>();
            for (Object v : values) {
                out.add(unwrapOtelValue(v));
            }
            return out;
        }
        return value;
    }

    private static String stringAttr(Map<String, Object> attrs, String key) {
        if (attrs == null) return null;
        Object v = attrs.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

}
