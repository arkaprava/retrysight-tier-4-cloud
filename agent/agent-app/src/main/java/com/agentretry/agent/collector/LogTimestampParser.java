/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.collector;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogTimestampParser {

    private static final List<String> INSTANT_FIELDS = List.of(
            "timestamp",
            "createdAt",
            "created_at",
            "time",
            "ts",
            "started_at",
            "startedAt",
            "start_ts"
    );

    private static final Pattern CURSOR_TIMESTAMP_TAG = Pattern.compile(
            "<timestamp>(.+?)</timestamp>",
            Pattern.DOTALL
    );

    private static final Pattern CURSOR_OFFSET = Pattern.compile("\\(UTC([+-]\\d+)\\)");

    private static final Pattern CODEX_ROLLOUT_FILENAME = Pattern.compile(
            "rollout-(\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2})-"
    );

    private static final DateTimeFormatter CURSOR_HUMAN_FORMAT = DateTimeFormatter.ofPattern(
            "EEEE, MMM d, yyyy, h:mm a",
            Locale.ENGLISH
    );

    private static final DateTimeFormatter CODEX_FILENAME_FORMAT = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd'T'HH-mm-ss"
    );

    private LogTimestampParser() {
    }

    public static Instant resolve(JsonNode node) {
        return resolve(node, null);
    }

    public static Instant resolve(JsonNode node, String sessionPath) {
        Instant instant = parseNode(node);
        if (instant != null) {
            return instant;
        }
        if (node != null && node.has("payload")) {
            instant = parseNode(node.get("payload"));
            if (instant != null) {
                return instant;
            }
        }
        if (node != null && node.has("data")) {
            instant = parseNode(node.get("data"));
            if (instant != null) {
                return instant;
            }
        }
        instant = parseCursorEmbeddedTimestamp(node);
        if (instant != null) {
            return instant;
        }
        instant = resolveFromSessionFile(sessionPath);
        return instant != null ? instant : Instant.now();
    }

    public static Instant resolveFromWarpStartTs(long startTs) {
        if (startTs <= 0) {
            return null;
        }
        if (startTs > 1_000_000_000_000L) {
            return Instant.ofEpochMilli(startTs);
        }
        return Instant.ofEpochSecond(startTs);
    }

    public static Instant resolveFromSessionFile(String sessionPath) {
        if (sessionPath == null || sessionPath.isBlank()) {
            return null;
        }
        Instant fromFilename = parseCodexRolloutFilename(sessionPath);
        if (fromFilename != null) {
            return fromFilename;
        }
        try {
            Path path = Path.of(sessionPath);
            if (!Files.exists(path)) {
                return null;
            }
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            if (attrs.creationTime().toMillis() > 0) {
                return attrs.creationTime().toInstant();
            }
            return Files.getLastModifiedTime(path).toInstant();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Instant parseNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String field : INSTANT_FIELDS) {
            if (!node.has(field)) {
                continue;
            }
            Instant parsed = parseValue(node.get(field));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private static Instant parseValue(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        if (value.isNumber()) {
            long raw = value.asLong();
            if (raw <= 0) {
                return null;
            }
            return raw > 1_000_000_000_000L ? Instant.ofEpochMilli(raw) : Instant.ofEpochSecond(raw);
        }
        if (!value.isTextual()) {
            return null;
        }
        String text = value.asText().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private static Instant parseCursorEmbeddedTimestamp(JsonNode node) {
        if (node == null) {
            return null;
        }
        String text = extractTextContent(node);
        if (text.isBlank()) {
            return null;
        }
        Matcher matcher = CURSOR_TIMESTAMP_TAG.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return parseCursorHumanTimestamp(matcher.group(1).trim());
    }

    private static String extractTextContent(JsonNode node) {
        JsonNode message = node.path("message");
        JsonNode content = message.path("content");
        if (content.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText("text"))) {
                    builder.append(block.path("text").asText(""));
                }
            }
            return builder.toString();
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (message.path("content").isTextual()) {
            return message.path("content").asText();
        }
        return node.path("text").asText("");
    }

    private static Instant parseCursorHumanTimestamp(String raw) {
        Matcher offsetMatcher = CURSOR_OFFSET.matcher(raw);
        ZoneOffset offset = ZoneOffset.UTC;
        String datePart = raw;
        if (offsetMatcher.find()) {
            int hours = Integer.parseInt(offsetMatcher.group(1));
            offset = ZoneOffset.ofHours(hours);
            datePart = raw.substring(0, offsetMatcher.start()).trim();
            if (datePart.endsWith(",")) {
                datePart = datePart.substring(0, datePart.length() - 1).trim();
            }
        }
        try {
            LocalDateTime local = LocalDateTime.parse(datePart, CURSOR_HUMAN_FORMAT);
            return local.atOffset(offset).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static Instant parseCodexRolloutFilename(String sessionPath) {
        Matcher matcher = CODEX_ROLLOUT_FILENAME.matcher(Path.of(sessionPath).getFileName().toString());
        if (!matcher.find()) {
            return null;
        }
        try {
            LocalDateTime local = LocalDateTime.parse(matcher.group(1), CODEX_FILENAME_FORMAT);
            return local.toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
