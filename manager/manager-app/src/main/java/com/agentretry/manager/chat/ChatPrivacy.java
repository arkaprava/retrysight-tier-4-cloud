/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.chat;

import java.util.regex.Pattern;

/** Redacts secrets and sensitive patterns before LLM / audit logging. */
public final class ChatPrivacy {
    private static final Pattern API_KEY = Pattern.compile(
            "(?i)(api[_-]?key|authorization|bearer|token|secret|password)\\s*[:=]\\s*['\"]?[^\\s'\"]+"
    );
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._\\-]+");
    private static final Pattern EMAIL = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
    );
    private static final Pattern ABS_PATH = Pattern.compile("(/Users|/home|/var|/opt)/[^\\s\"']+");

    private ChatPrivacy() {}

    public static String redact(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        // Bearer tokens first so "Authorization: Bearer …" is fully covered.
        String out = BEARER.matcher(input).replaceAll("Bearer [REDACTED]");
        out = API_KEY.matcher(out).replaceAll("$1=[REDACTED]");
        out = EMAIL.matcher(out).replaceAll("[REDACTED_EMAIL]");
        out = ABS_PATH.matcher(out).replaceAll("[REDACTED_PATH]");
        return out;
    }

    public static String truncate(String input, int maxChars) {
        if (input == null) {
            return "";
        }
        if (input.length() <= maxChars) {
            return input;
        }
        return input.substring(0, maxChars) + "…[truncated]";
    }
}
