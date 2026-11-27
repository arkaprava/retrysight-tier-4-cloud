/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.collector;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Rough token estimate for transcripts that omit provider usage metadata.
 * Uses ~4 characters per token, a common heuristic for English/code text.
 */
public final class TokenEstimator {

    private static final int CHARS_PER_TOKEN = 4;

    private TokenEstimator() {
    }

    public static long estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, text.length() / CHARS_PER_TOKEN);
    }

    public static long estimateMessage(JsonNode message) {
        if (message == null || message.isMissingNode()) {
            return 0;
        }
        return estimate(extractText(message));
    }

    public static String extractText(JsonNode message) {
        JsonNode content = message.path("content");
        if (content.isTextual()) {
            return content.asText();
        }
        if (!content.isArray()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (JsonNode block : content) {
            String type = block.path("type").asText();
            if ("text".equals(type)) {
                sb.append(block.path("text").asText()).append(' ');
            } else if ("tool_use".equals(type) || "tool_result".equals(type)) {
                sb.append(block.path("name").asText()).append(' ');
                JsonNode input = block.path("input");
                if (!input.isMissingNode()) {
                    sb.append(input.toString()).append(' ');
                }
                JsonNode result = block.path("content");
                if (!result.isMissingNode()) {
                    sb.append(result.toString()).append(' ');
                }
            }
        }
        return sb.toString().trim();
    }
}
