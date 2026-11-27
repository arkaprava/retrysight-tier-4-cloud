/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.eval;

import com.agentretry.contract.SessionReplayFormat;
import com.agentretry.manager.chat.LocalLlmClient;
import com.agentretry.manager.config.ChatProperties;
import com.agentretry.manager.config.EvalProperties;
import com.agentretry.manager.domain.EvalDefinitionEntity;
import com.agentretry.manager.domain.TaskEntity;
import com.agentretry.manager.replay.SessionReplayService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LlmJudgeEvaluator {
    private static final Logger log = LoggerFactory.getLogger(LlmJudgeEvaluator.class);
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*}", Pattern.MULTILINE);

    private final LocalLlmClient llmClient;
    private final ChatProperties chatProperties;
    private final EvalProperties evalProperties;
    private final SessionReplayService sessionReplayService;
    private final ObjectMapper objectMapper;

    public LlmJudgeEvaluator(
            LocalLlmClient llmClient,
            ChatProperties chatProperties,
            EvalProperties evalProperties,
            SessionReplayService sessionReplayService,
            ObjectMapper objectMapper
    ) {
        this.llmClient = llmClient;
        this.chatProperties = chatProperties;
        this.evalProperties = evalProperties;
        this.sessionReplayService = sessionReplayService;
        this.objectMapper = objectMapper;
    }

    public ManagedEvaluatorEngine.EvalOutcome evaluate(EvalDefinitionEntity definition, TaskEntity task) {
        return evaluate(definition, task, null);
    }

    public ManagedEvaluatorEngine.EvalOutcome evaluate(
            EvalDefinitionEntity definition,
            TaskEntity task,
            String promptOverride
    ) {
        if (!llmClient.isReachable()) {
            return ManagedEvaluatorEngine.EvalOutcome.skipped("Local LLM (Ollama) is unavailable.");
        }
        String template = promptOverride != null && !promptOverride.isBlank()
                ? promptOverride
                : definition.getPromptTemplate();
        if (template == null || template.isBlank()) {
            return ManagedEvaluatorEngine.EvalOutcome.skipped("LLM judge prompt template is empty.");
        }
        try {
            String replay = sessionReplayService.render(task.getId(), SessionReplayFormat.PROMPT);
            String excerpt = truncate(replay, evalProperties.getReplayExcerptMaxChars());
            String userPrompt = template
                    .replace("{{task_description}}", nullToEmpty(task.getDescription()))
                    .replace("{{source_tool}}", task.getSourceTool() != null ? task.getSourceTool().name() : "")
                    .replace("{{retry_count}}", String.valueOf(task.getRetryCount()))
                    .replace("{{session_replay}}", excerpt);

            LocalLlmClient.LlmResponse response = llmClient.chat(
                    chatProperties.getModel(),
                    List.of(
                            Map.of("role", "system", "content", """
                                    You are an evaluation judge for coding-agent sessions.
                                    Respond with JSON only: {"label":"PASS|FAIL|WARN","score":0-100,"explanation":"..."}
                                    """),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    null
            );
            return parseResponse(response.content());
        } catch (Exception ex) {
            log.warn("LLM judge failed for task {}: {}", task.getId(), ex.getMessage());
            return ManagedEvaluatorEngine.EvalOutcome.skipped("LLM judge error: " + ex.getMessage());
        }
    }

    private ManagedEvaluatorEngine.EvalOutcome parseResponse(String content) {
        if (content == null || content.isBlank()) {
            return ManagedEvaluatorEngine.EvalOutcome.skipped("LLM judge returned empty response.");
        }
        try {
            JsonNode node = objectMapper.readTree(extractJson(content));
            String labelText = node.path("label").asText("WARN").toUpperCase(Locale.ROOT);
            EvalRunLabel label = switch (labelText) {
                case "PASS" -> EvalRunLabel.PASS;
                case "FAIL" -> EvalRunLabel.FAIL;
                default -> EvalRunLabel.WARN;
            };
            double score = node.path("score").asDouble(50);
            String explanation = node.path("explanation").asText(content);
            return new ManagedEvaluatorEngine.EvalOutcome(label, score, explanation, false);
        } catch (Exception ex) {
            return ManagedEvaluatorEngine.EvalOutcome.skipped("Could not parse LLM judge JSON.");
        }
    }

    private static String extractJson(String content) {
        Matcher matcher = JSON_BLOCK.matcher(content);
        if (matcher.find()) {
            return matcher.group();
        }
        return content;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value == null ? "" : value;
        }
        return value.substring(0, max) + "\n… [truncated]";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
