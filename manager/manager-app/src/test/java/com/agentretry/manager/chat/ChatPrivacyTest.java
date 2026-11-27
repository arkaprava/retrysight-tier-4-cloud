/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatPrivacyTest {

    @Test
    void redactsBearerAndApiKeys() {
        String raw = "Authorization: Bearer abc.def.ghi api_key=sk-secret-123";
        String out = ChatPrivacy.redact(raw);
        assertFalse(out.contains("abc.def.ghi"));
        assertFalse(out.contains("sk-secret-123"));
        assertTrue(out.contains("[REDACTED]"));
    }

    @Test
    void redactsEmailAndPaths() {
        String raw = "user alice@example.com path /Users/alice/Projects/secret";
        String out = ChatPrivacy.redact(raw);
        assertFalse(out.contains("alice@example.com"));
        assertFalse(out.contains("/Users/alice"));
        assertTrue(out.contains("[REDACTED_EMAIL]"));
        assertTrue(out.contains("[REDACTED_PATH]"));
    }

    @Test
    void truncatesLongText() {
        String out = ChatPrivacy.truncate("abcdefghij", 5);
        assertEquals("abcde…[truncated]", out);
    }
}
