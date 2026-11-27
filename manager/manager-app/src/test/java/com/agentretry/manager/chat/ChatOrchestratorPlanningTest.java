/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.chat;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden-style checks for deterministic tool planning (no Spring context).
 */
class ChatOrchestratorPlanningTest {

    @SuppressWarnings("unchecked")
    @Test
    void plansCostAndRetryTools() throws Exception {
        Method m = ChatOrchestrator.class.getDeclaredMethod("planTools", String.class, ChatDtos.ChatPageContext.class);
        m.setAccessible(true);
        ChatOrchestrator orch = new ChatOrchestrator(null, null, null, null, null);
        List<String> tools = (List<String>) m.invoke(orch, "show cost and retry waste", null);
        assertTrue(tools.contains("get_cost_summary"));
        assertTrue(tools.contains("get_retry_analysis"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void plansCostForSpentWordingAndCostPage() throws Exception {
        Method m = ChatOrchestrator.class.getDeclaredMethod("planTools", String.class, ChatDtos.ChatPageContext.class);
        m.setAccessible(true);
        ChatOrchestrator orch = new ChatOrchestrator(null, null, null, null, null);
        List<String> spent = (List<String>) m.invoke(orch, "Who spent the most this week?", null);
        assertTrue(spent.contains("get_cost_summary"), () -> "tools=" + spent);

        ChatDtos.ChatPageContext costPage = new ChatDtos.ChatPageContext(
                "/cost", null, null, null, null, null, null
        );
        List<String> fromPath = (List<String>) m.invoke(orch, "give me an overview", costPage);
        assertTrue(fromPath.contains("get_cost_summary"), () -> "tools=" + fromPath);
    }

    @SuppressWarnings("unchecked")
    @Test
    void plansTaskReplayWhenContextPresent() throws Exception {
        Method m = ChatOrchestrator.class.getDeclaredMethod("planTools", String.class, ChatDtos.ChatPageContext.class);
        m.setAccessible(true);
        ChatOrchestrator orch = new ChatOrchestrator(null, null, null, null, null);
        ChatDtos.ChatPageContext ctx = new ChatDtos.ChatPageContext(
                "/tasks/11111111-1111-1111-1111-111111111111",
                null, null, null, null,
                "11111111-1111-1111-1111-111111111111",
                null
        );
        List<String> tools = (List<String>) m.invoke(orch, "explain this session replay", ctx);
        assertTrue(tools.contains("get_session_replay_prompt"));
    }
}
