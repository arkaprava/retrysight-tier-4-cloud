/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.otel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

record OtelLogRecord(
        String eventName,
        String serviceName,
        String sessionId,
        String promptId,
        String organizationId,
        String userEmail,
        String userAccountId,
        Integer sequence,
        Instant timestamp,
        List<String> workspaceHostPaths,
        Map<String, Object> attributes
) {
    static OtelLogRecord from(Map<String, Object> logRecord, Map<String, Object> resourceAttrs, String serviceName) {
        Map<String, Object> attrs = new HashMap<>();
        if (resourceAttrs != null) {
            attrs.putAll(resourceAttrs);
        }
        attrs.putAll(CoworkOtelMapper.flattenAttributes(logRecord.get("attributes")));

        // body may be string or { stringValue }
        Object body = logRecord.get("body");
        String bodyEvent = null;
        if (body instanceof Map<?, ?> bodyMap) {
            Object sv = bodyMap.get("stringValue");
            if (sv != null) {
                bodyEvent = String.valueOf(sv);
            }
        } else if (body instanceof String s && !s.isBlank()) {
            bodyEvent = s;
        }

        String eventName = first(
                string(attrs, "event.name"),
                string(attrs, "event_name"),
                bodyEvent
        );
        Instant ts = parseTime(logRecord.get("timeUnixNano"), logRecord.get("observedTimeUnixNano"), attrs);

        return new OtelLogRecord(
                eventName,
                first(serviceName, string(attrs, "service.name")),
                string(attrs, "session.id"),
                string(attrs, "prompt.id"),
                string(attrs, "organization.id"),
                string(attrs, "user.email"),
                first(string(attrs, "user.account_id"), string(attrs, "user.account_uuid")),
                intOrNull(attrs.get("event.sequence")),
                ts,
                stringList(attrs.get("workspace.host_paths")),
                attrs
        );
    }

    @SuppressWarnings("unchecked")
    static OtelLogRecord fromFlat(Map<String, Object> flat) {
        Map<String, Object> attrs = new HashMap<>(flat);
        return new OtelLogRecord(
                first(string(attrs, "event.name"), string(attrs, "eventName"), string(attrs, "name")),
                first(string(attrs, "service.name"), string(attrs, "serviceName"), "cowork"),
                first(string(attrs, "session.id"), string(attrs, "sessionId")),
                first(string(attrs, "prompt.id"), string(attrs, "promptId")),
                first(string(attrs, "organization.id"), string(attrs, "organizationId")),
                first(string(attrs, "user.email"), string(attrs, "userEmail")),
                first(string(attrs, "user.account_id"), string(attrs, "userAccountId")),
                intOrNull(firstObj(attrs.get("event.sequence"), attrs.get("sequence"))),
                parseTime(null, null, attrs),
                stringList(firstObj(attrs.get("workspace.host_paths"), attrs.get("workspaceHostPaths"))),
                attrs
        );
    }

    String attrString(String key) {
        Object v = attributes.get(key);
        return v == null ? null : String.valueOf(v);
    }

    long attrLong(String key) {
        Object v = attributes.get(key);
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    Double attrDouble(String key) {
        Object v = attributes.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Instant parseTime(Object timeUnixNano, Object observedNano, Map<String, Object> attrs) {
        Instant fromNano = nanoToInstant(timeUnixNano);
        if (fromNano != null) return fromNano;
        fromNano = nanoToInstant(observedNano);
        if (fromNano != null) return fromNano;
        String iso = string(attrs, "event.timestamp");
        if (iso == null) {
            iso = string(attrs, "timestamp");
        }
        if (iso != null && !iso.isBlank()) {
            try {
                return Instant.parse(iso);
            } catch (Exception ignored) {
                // fall through
            }
        }
        return Instant.now();
    }

    private static Instant nanoToInstant(Object nano) {
        if (nano == null) return null;
        try {
            long n = nano instanceof Number num ? num.longValue() : Long.parseLong(String.valueOf(nano));
            if (n <= 0) return null;
            return Instant.ofEpochSecond(n / 1_000_000_000L, n % 1_000_000_000L);
        } catch (Exception e) {
            return null;
        }
    }

    private static String string(Map<String, Object> attrs, String key) {
        Object v = attrs.get(key);
        if (v == null) return null;
        String s = String.valueOf(v);
        return s.isBlank() || "null".equals(s) ? null : s;
    }

    private static Integer intOrNull(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object v) {
        if (v == null) return List.of();
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null) out.add(String.valueOf(item));
            }
            return out;
        }
        String s = String.valueOf(v);
        if (s.isBlank()) return List.of();
        return List.of(s);
    }

    private static String first(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static Object firstObj(Object a, Object b) {
        return a != null ? a : b;
    }
}
