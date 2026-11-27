/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.chat;

import java.util.List;
import java.util.Map;

public final class ChatDtos {
    private ChatDtos() {}

    public record ChatMessageDto(String role, String content) {}

    public record ChatPageContext(
            String path,
            String user,
            String from,
            String to,
            String sourceTool,
            String taskId,
            String agentId
    ) {}

    public record ChatRequest(
            List<ChatMessageDto> messages,
            ChatPageContext pageContext,
            String model
    ) {}

    public record ChatHealth(
            boolean enabled,
            boolean runtimeReachable,
            String model,
            String ollamaBaseUrl,
            List<String> availableModels,
            boolean deterministicFallbackEnabled,
            String statusMessage
    ) {}

    public record ChatAuditEntry(
            String at,
            String principal,
            String model,
            String mode,
            List<String> toolsUsed,
            int promptChars,
            String path
    ) {}

    public record ToolSpec(String name, String description, Map<String, Object> parameters) {}
}
