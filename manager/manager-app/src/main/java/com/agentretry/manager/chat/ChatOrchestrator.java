/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.chat;

import com.agentretry.manager.config.ChatProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ChatOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrator.class);

    private final ChatProperties properties;
    private final LocalLlmClient llmClient;
    private final ChatToolRegistry toolRegistry;
    private final ChatAuditService auditService;
    private final ObjectMapper objectMapper;
    private final ChatDeterministicFormatter deterministicFormatter;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatOrchestrator(
            ChatProperties properties,
            LocalLlmClient llmClient,
            ChatToolRegistry toolRegistry,
            ChatAuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.deterministicFormatter = new ChatDeterministicFormatter(objectMapper);
    }

    public SseEmitter stream(ChatDtos.ChatRequest request, String principal) {
        SseEmitter emitter = new SseEmitter(properties.getReadTimeoutMs() + 30_000L);
        executor.execute(() -> {
            try {
                run(request, principal, emitter);
                emitter.complete();
            } catch (Exception e) {
                log.warn("Chat stream failed: {}", e.getMessage());
                try {
                    send(emitter, "error", Map.of("message", e.getMessage() == null ? "Chat failed" : e.getMessage()));
                } catch (Exception ignored) {
                    // ignore
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /** Non-streaming completion for tests and simple clients. */
    public Map<String, Object> complete(ChatDtos.ChatRequest request, String principal) throws Exception {
        List<ChatDtos.ChatMessageDto> history = request.messages() == null ? List.of() : request.messages();
        if (history.isEmpty()) {
            throw new IllegalArgumentException("messages required");
        }
        String lastUser = history.get(history.size() - 1).content();
        String model = request.model() != null && !request.model().isBlank() ? request.model() : properties.getModel();
        ChatDtos.ChatPageContext ctx = request.pageContext();
        List<String> toolsUsed = new ArrayList<>();
        boolean reachable = llmClient.isReachable();
        String mode;
        String answer;
        if (reachable) {
            mode = "ollama";
            answer = runWithLlm(history, ctx, model, toolsUsed, null);
        } else if (properties.isAllowDeterministicFallback()) {
            mode = "deterministic";
            answer = runDeterministic(lastUser, ctx, toolsUsed, null);
        } else {
            throw new IllegalStateException("Local LLM (Ollama) is not reachable at " + properties.getOllamaBaseUrl());
        }
        auditService.record(
                principal,
                model,
                mode,
                toolsUsed,
                lastUser == null ? 0 : lastUser.length(),
                ctx == null ? null : ctx.path()
        );
        return Map.of("mode", mode, "content", answer, "toolsUsed", toolsUsed, "model", model);
    }

    private void run(ChatDtos.ChatRequest request, String principal, SseEmitter emitter) throws Exception {
        List<ChatDtos.ChatMessageDto> history = request.messages() == null ? List.of() : request.messages();
        if (history.isEmpty()) {
            throw new IllegalArgumentException("messages required");
        }
        String lastUser = history.get(history.size() - 1).content();
        String model = request.model() != null && !request.model().isBlank() ? request.model() : properties.getModel();
        ChatDtos.ChatPageContext ctx = request.pageContext();
        List<String> toolsUsed = new ArrayList<>();

        boolean reachable = llmClient.isReachable();
        String mode;
        String answer;
        if (reachable) {
            mode = "ollama";
            send(emitter, "status", Map.of("mode", mode, "model", model));
            answer = runWithLlm(history, ctx, model, toolsUsed, emitter);
        } else if (properties.isAllowDeterministicFallback()) {
            mode = "deterministic";
            send(emitter, "status", Map.of(
                    "mode", mode,
                    "message", "Local model unreachable; using deterministic tool insights."
            ));
            answer = runDeterministic(lastUser, ctx, toolsUsed, emitter);
        } else {
            throw new IllegalStateException("Local LLM (Ollama) is not reachable at " + properties.getOllamaBaseUrl());
        }

        auditService.record(
                principal,
                model,
                mode,
                toolsUsed,
                lastUser == null ? 0 : lastUser.length(),
                ctx == null ? null : ctx.path()
        );
        send(emitter, "done", Map.of("mode", mode, "toolsUsed", toolsUsed, "chars", answer == null ? 0 : answer.length()));
    }

    private String runWithLlm(
            List<ChatDtos.ChatMessageDto> history,
            ChatDtos.ChatPageContext ctx,
            String model,
            List<String> toolsUsed,
            SseEmitter emitter
    ) throws Exception {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt(ctx)));
        int maxHist = properties.getMaxHistoryMessages();
        List<ChatDtos.ChatMessageDto> trimmed = history.size() > maxHist
                ? history.subList(history.size() - maxHist, history.size())
                : history;
        for (ChatDtos.ChatMessageDto m : trimmed) {
            if (m == null || m.role() == null || m.content() == null) continue;
            messages.add(Map.of("role", m.role(), "content", ChatPrivacy.redact(m.content())));
        }

        List<ChatDtos.ToolSpec> tools = toolRegistry.specs();
        String finalAnswer = "";
        for (int round = 0; round < properties.getMaxToolRounds(); round++) {
            LocalLlmClient.LlmResponse response = llmClient.chat(model, messages, tools);
            if (response.hasToolCalls()) {
                Map<String, Object> assistantMsg = new LinkedHashMap<>();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", response.content() == null ? "" : response.content());
                if (response.rawMessage() != null && response.rawMessage().has("tool_calls")) {
                    assistantMsg.put("tool_calls", objectMapper.convertValue(
                            response.rawMessage().path("tool_calls"), List.class));
                }
                messages.add(assistantMsg);

                for (LocalLlmClient.ToolCall call : response.toolCalls()) {
                    toolsUsed.add(call.name());
                    if (emitter != null) {
                        send(emitter, "tool_start", Map.of("name", call.name(), "id", call.id()));
                    }
                    String result = toolRegistry.execute(call.name(), call.argumentsJson(), ctx);
                    if (emitter != null) {
                        send(emitter, "tool_result", Map.of("name", call.name(), "id", call.id(), "chars", result.length()));
                    }
                    Map<String, Object> toolMsg = new LinkedHashMap<>();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", call.id());
                    toolMsg.put("content", result);
                    messages.add(toolMsg);
                }
                continue;
            }
            finalAnswer = response.content() == null ? "" : response.content();
            break;
        }
        if (finalAnswer.isBlank()) {
            finalAnswer = "I gathered tool data but could not synthesize an answer. Try rephrasing or check Ollama model tool-calling support.";
        }
        if (emitter != null) {
            streamAnswer(emitter, finalAnswer);
        }
        return finalAnswer;
    }

    private String runDeterministic(
            String userMessage,
            ChatDtos.ChatPageContext ctx,
            List<String> toolsUsed,
            SseEmitter emitter
    ) throws Exception {
        String lower = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        List<String> planned = planTools(lower, ctx);
        StringBuilder sb = new StringBuilder();
        sb.append("### Insights\n\n");
        sb.append("_Local Ollama was unreachable — answered with live platform tools._\n\n");
        for (String tool : planned) {
            toolsUsed.add(tool);
            if (emitter != null) {
                send(emitter, "tool_start", Map.of("name", tool));
            }
            String args = "{}";
            if ("get_task".equals(tool) || "get_session_replay_prompt".equals(tool)) {
                String taskId = ctx != null ? ctx.taskId() : null;
                if (taskId != null) {
                    args = "{\"taskId\":\"" + taskId + "\"}";
                }
            }
            if ("generate_report_preview".equals(tool)) {
                args = "{\"dataset\":\"EXECUTIVE_SUMMARY\"}";
            }
            String result = toolRegistry.execute(tool, args, ctx);
            if (emitter != null) {
                send(emitter, "tool_result", Map.of("name", tool, "chars", result.length()));
            }
            sb.append(deterministicFormatter.format(tool, result)).append('\n');
        }
        sb.append(suggestedLinks(ctx));
        String answer = sb.toString();
        if (emitter != null) {
            streamAnswer(emitter, answer);
        }
        return answer;
    }

    private List<String> planTools(String lower, ChatDtos.ChatPageContext ctx) {
        List<String> tools = new ArrayList<>();
        String path = ctx != null && ctx.path() != null ? ctx.path().toLowerCase(Locale.ROOT) : "";

        boolean costIntent = containsAny(lower, "cost", "spend", "spent", "spending", "budget", "billing", "usd", "token")
                || path.startsWith("/cost")
                || (containsAny(lower, "who", "most", "highest", "top") && containsAny(lower, "week", "month", "user", "developer"));
        boolean retryIntent = containsAny(lower, "retry", "retries", "waste") || path.startsWith("/retries");
        boolean overviewIntent = containsAny(lower, "overview", "performance", "summary", "dashboard", "how are we");

        if (costIntent) {
            tools.add("get_cost_summary");
        }
        if (retryIntent) {
            tools.add("get_retry_analysis");
        }
        if (overviewIntent && !tools.contains("get_cost_summary")) {
            tools.add("get_dashboard_summary");
        }
        if (containsAny(lower, "agent", "collector") || path.startsWith("/agents")) {
            tools.add("list_agents");
        }
        if (containsAny(lower, "user", "developer", "who")) {
            // Prefer cost-by-user when asking who spent; otherwise list users.
            if (!tools.contains("get_cost_summary")) {
                tools.add("list_users");
            }
        }
        if (containsAny(lower, "report") || path.startsWith("/reports")) {
            tools.add("generate_report_preview");
        }
        if (containsAny(lower, "replay", "prompt", "session")) {
            if (ctx != null && ctx.taskId() != null && !ctx.taskId().isBlank()) {
                tools.add("get_session_replay_prompt");
            }
        }
        if (containsAny(lower, "task") || (ctx != null && ctx.taskId() != null && !ctx.taskId().isBlank())) {
            if (ctx != null && ctx.taskId() != null && !ctx.taskId().isBlank()
                    && containsAny(lower, "this", "current", "summarize")) {
                tools.add("get_task");
            } else if (!tools.contains("get_task") && !costIntent) {
                tools.add("list_tasks");
            }
        }
        if (tools.isEmpty()) {
            tools.add("get_dashboard_summary");
            tools.add("list_tasks");
        }
        return tools;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private void streamAnswer(SseEmitter emitter, String answer) throws IOException {
        int chunk = 280;
        for (int i = 0; i < answer.length(); i += chunk) {
            String part = answer.substring(i, Math.min(answer.length(), i + chunk));
            send(emitter, "delta", Map.of("content", part));
        }
        send(emitter, "final", Map.of("content", answer));
    }

    private String systemPrompt(ChatDtos.ChatPageContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are Insights Chat for Agent Performance Monitor. ");
        sb.append("Answer using tools only; do not invent metrics. ");
        sb.append("Be concise. Prefer numbers, short bullets, and deep links like /tasks/{id}, /cost, /retries, /reports. ");
        sb.append("Never reveal API keys, JWTs, or absolute filesystem paths. ");
        sb.append("If data is missing, say so and suggest which page to open.\n");
        if (ctx != null) {
            sb.append("Page context: path=").append(nullToDash(ctx.path()));
            sb.append(" user=").append(nullToDash(ctx.user()));
            sb.append(" from=").append(nullToDash(ctx.from()));
            sb.append(" to=").append(nullToDash(ctx.to()));
            sb.append(" sourceTool=").append(nullToDash(ctx.sourceTool()));
            sb.append(" taskId=").append(nullToDash(ctx.taskId()));
            sb.append(" agentId=").append(nullToDash(ctx.agentId()));
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String suggestedLinks(ChatDtos.ChatPageContext ctx) {
        StringBuilder sb = new StringBuilder("### Explore\n");
        sb.append("- [Dashboard](/)\n- [Cost](/cost)\n- [Retries](/retries)\n- [Tasks](/tasks)\n- [Reports](/reports)\n");
        if (ctx != null && ctx.taskId() != null && !ctx.taskId().isBlank()) {
            sb.append("- [This task](/tasks/").append(ctx.taskId()).append(")\n");
        }
        return sb.toString();
    }

    private static String nullToDash(String v) {
        return v == null || v.isBlank() ? "-" : v;
    }

    private void send(SseEmitter emitter, String event, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(event).data(data));
    }
}
