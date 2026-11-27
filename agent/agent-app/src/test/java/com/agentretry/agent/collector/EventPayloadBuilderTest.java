/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.collector;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventPayloadBuilderTest {

    private final EventPayloadBuilder builder = new EventPayloadBuilder(new com.agentretry.agent.config.AgentProperties());

    @Test
    void editEventSummarizesSearchReplace() {
        var input = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                .put("file_path", "src/App.tsx")
                .put("old_string", "const foo = 1")
                .put("new_string", "const foo = 2");

        Map<String, Object> payload = builder.editEvent("session-1", "strreplace", "src/App.tsx", 1, true, input);

        assertEquals("rewrite", payload.get("action"));
        assertEquals(2, payload.get("editOrdinal"));
        assertEquals("search_replace", payload.get("changeType"));
        assertTrue(payload.get("changeSummary").toString().contains("src/App.tsx"));
        assertEquals("const foo = 1", payload.get("oldPreview"));
        assertEquals("const foo = 2", payload.get("newPreview"));
    }

    @Test
    void diffRejectedIncludesUserContext() {
        builder.recordUserContext("session-1", "Please fix the auth bug without changing tests");

        Map<String, Object> payload = builder.diffRejected(
                "session-1",
                2,
                "failed",
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("reason", "User rejected diff"),
                java.util.List.of("src/Auth.java")
        );

        assertEquals("rejected", payload.get("action"));
        assertEquals("User rejected diff", payload.get("reason"));
        assertEquals("Please fix the auth bug without changing tests", payload.get("context"));
        assertEquals(java.util.List.of("src/Auth.java"), payload.get("affectedFiles"));
    }
}
