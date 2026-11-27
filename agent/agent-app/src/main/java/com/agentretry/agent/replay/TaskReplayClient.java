/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.replay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Fetches AI-ready session replay documents from the manager for a tracked task.
 */
@Component
public class TaskReplayClient {

    private static final Logger log = LoggerFactory.getLogger(TaskReplayClient.class);

    private final WebClient managerWebClient;

    public TaskReplayClient(WebClient managerWebClient) {
        this.managerWebClient = managerWebClient;
    }

    public String fetchPromptReplay(UUID taskId) {
        return fetchReplay(taskId, "PROMPT");
    }

    public String fetchMarkdownReplay(UUID taskId) {
        return fetchReplay(taskId, "MARKDOWN");
    }

    public String fetchJsonReplay(UUID taskId) {
        return fetchReplay(taskId, "JSON");
    }

    private String fetchReplay(UUID taskId, String format) {
        try {
            return managerWebClient.get()
                    .uri("/api/v1/tasks/{taskId}/replay?format={format}", taskId, format)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                    .block();
        } catch (Exception ex) {
            log.warn("Failed to fetch session replay for task {}: {}", taskId, ex.getMessage());
            return null;
        }
    }
}
