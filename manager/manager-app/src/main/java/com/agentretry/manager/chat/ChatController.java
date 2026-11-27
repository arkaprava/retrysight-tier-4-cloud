/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.chat;

import com.agentretry.manager.config.ChatProperties;
import com.agentretry.manager.security.RoleGuard;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatProperties properties;
    private final ChatOrchestrator orchestrator;
    private final LocalLlmClient llmClient;
    private final ChatRateLimiter rateLimiter;
    private final ChatAuditService auditService;
    private final ChatToolRegistry toolRegistry;

    public ChatController(
            ChatProperties properties,
            ChatOrchestrator orchestrator,
            LocalLlmClient llmClient,
            ChatRateLimiter rateLimiter,
            ChatAuditService auditService,
            ChatToolRegistry toolRegistry
    ) {
        this.properties = properties;
        this.orchestrator = orchestrator;
        this.llmClient = llmClient;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
        this.toolRegistry = toolRegistry;
    }

    @GetMapping("/health")
    public ChatDtos.ChatHealth health() {
        boolean reachable = llmClient.isReachable();
        List<String> models = reachable ? llmClient.listModels() : List.of();
        String status = !properties.isEnabled()
                ? "disabled"
                : reachable
                ? "ready"
                : properties.isAllowDeterministicFallback()
                ? "degraded"
                : "unavailable";
        String message = switch (status) {
            case "disabled" -> "Insights chat is disabled";
            case "ready" -> "Ollama reachable";
            case "degraded" -> "Ollama unreachable; deterministic tool mode available";
            default -> "Ollama unreachable and fallback disabled";
        };
        return new ChatDtos.ChatHealth(
                properties.isEnabled(),
                reachable,
                properties.getModel(),
                properties.getOllamaBaseUrl(),
                models,
                properties.isAllowDeterministicFallback(),
                message
        );
    }

    @GetMapping("/tools")
    public List<ChatDtos.ToolSpec> tools() {
        requireEnabled();
        return toolRegistry.specs();
    }

    @GetMapping("/audit")
    public List<ChatDtos.ChatAuditEntry> audit(
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication
    ) {
        requireEnabled();
        RoleGuard.requireSecadmin(authentication);
        return auditService.recent(Math.min(200, Math.max(1, limit)));
    }

    @GetMapping("/suggestions")
    public Map<String, Object> suggestions(@RequestParam(required = false) String path) {
        requireEnabled();
        String p = path == null ? "/" : path;
        List<String> prompts;
        if (p.startsWith("/tasks/") && p.length() > "/tasks/".length()) {
            prompts = List.of(
                    "Summarize this task's retries and cost",
                    "What does the session replay suggest went wrong?",
                    "How does this task compare to others this week?"
            );
        } else if (p.startsWith("/cost")) {
            prompts = List.of(
                    "Who spent the most this week?",
                    "What is our retry waste ratio?",
                    "How much did Claude Cowork cost this week?"
            );
        } else if (p.startsWith("/retries")) {
            prompts = List.of(
                    "Which model has the highest retry rate?",
                    "Show top retry tasks",
                    "Where is retry waste concentrated?"
            );
        } else if (p.startsWith("/agents")) {
            prompts = List.of(
                    "Which collectors are active?",
                    "List developers and agent counts",
                    "Any collectors with stale heartbeats?"
            );
        } else if (p.startsWith("/reports")) {
            prompts = List.of(
                    "Preview an executive summary report",
                    "What should I include in a weekly cost report?",
                    "Summarize retry waste for reporting"
            );
        } else {
            prompts = List.of(
                    "Give me this week's performance overview",
                    "Where are retries and cost concentrated?",
                    "List recent high-retry tasks"
            );
        }
        return Map.of("path", p, "suggestions", prompts);
    }

    @PostMapping(value = "", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody ChatDtos.ChatRequest request, Authentication authentication) {
        requireEnabled();
        String principal = authentication == null ? "anonymous" : String.valueOf(authentication.getName());
        rateLimiter.check(principal);
        return orchestrator.stream(request, principal);
    }

    @PostMapping(value = "/message", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> chatSync(@RequestBody ChatDtos.ChatRequest request, Authentication authentication)
            throws Exception {
        requireEnabled();
        String principal = authentication == null ? "anonymous" : String.valueOf(authentication.getName());
        rateLimiter.check(principal);
        return ResponseEntity.ok(orchestrator.complete(request, principal));
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Insights chat disabled");
        }
    }
}
