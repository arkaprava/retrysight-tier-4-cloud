/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.replay;

import com.agentretry.contract.SessionReplayFormat;
import com.agentretry.contract.SourceTool;
import com.agentretry.manager.domain.TaskEntity;
import com.agentretry.manager.domain.TaskEventEntity;
import com.agentretry.manager.domain.TokenUsageEntity;
import com.agentretry.manager.graphql.EventTokenUsage;
import com.agentretry.manager.service.EventTokenUsageMatcher;
import com.agentretry.manager.service.ModelPricingService;
import com.agentretry.manager.service.QueryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SessionReplayService {

    private final QueryService queryService;
    private final ObjectMapper objectMapper;
    private final ModelPricingService modelPricingService;

    public SessionReplayService(
            QueryService queryService,
            ObjectMapper objectMapper,
            ModelPricingService modelPricingService
    ) {
        this.queryService = queryService;
        this.objectMapper = objectMapper;
        this.modelPricingService = modelPricingService;
    }

    public TaskSessionReplay buildReplay(UUID taskId) {
        TaskEntity task = requireTask(taskId);
        List<TaskEventEntity> events = queryService.findTaskEvents(taskId);
        List<TokenUsageEntity> tokenTurns = queryService.findTokenUsage(taskId);
        Map<TaskEventEntity, EventTokenUsage> eventTokens = queryService.resolveEventTokenUsage(events);

        Set<String> matchedTurnKeys = new LinkedHashSet<>();
        List<SessionReplayEntry> entries = new ArrayList<>();

        for (TaskEventEntity event : events) {
            EventTokenUsage tokenUsage = eventTokens.get(event);
            if (tokenUsage != null) {
                EventTokenUsageMatcher.attributeUsage(event, tokenTurns, modelPricingService)
                        .ifPresent(attr -> matchedTurnKeys.add(attr.dedupeKey()));
            }
            entries.add(SessionReplayEntry.fromEvent(event, tokenUsage, SessionReplayNarrator.describeEvent(event)));
        }

        for (TokenUsageEntity turn : tokenTurns) {
            String key = "turn:" + taskId + ":" + turn.getTurnIndex();
            if (matchedTurnKeys.contains(key)) {
                continue;
            }
            entries.add(SessionReplayEntry.fromTokenTurn(
                    turn,
                    SessionReplayNarrator.describeTokenTurn(turn)
            ));
        }

        entries.sort(Comparator
                .comparing(SessionReplayEntry::occurredAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(SessionReplayEntry::sequence, Comparator.nullsLast(Comparator.naturalOrder())));

        return new TaskSessionReplay(
                1,
                task.getId(),
                task.getDescription(),
                task.getSourceTool(),
                task.getStatus().name(),
                task.getRetryCount(),
                task.getExternalSessionId(),
                task.getStartedAt(),
                task.getCompletedAt(),
                task.getMetadata(),
                task.getAgent().getHostname(),
                task.getAgent().getDeveloperEmail(),
                queryService.summarizeTaskTokens(taskId),
                entries,
                buildInstructions(task)
        );
    }

    public String render(UUID taskId, SessionReplayFormat format) {
        TaskSessionReplay replay = buildReplay(taskId);
        return switch (format) {
            case JSON -> renderJson(replay);
            case MARKDOWN -> renderMarkdown(replay, false);
            case PROMPT -> renderMarkdown(replay, true);
        };
    }

    private TaskEntity requireTask(UUID taskId) {
        TaskEntity task = queryService.findTask(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found: " + taskId);
        }
        return task;
    }

    private String renderJson(TaskSessionReplay replay) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(replay);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize session replay", ex);
        }
    }

    private String renderMarkdown(TaskSessionReplay replay, boolean promptMode) {
        StringBuilder markdown = new StringBuilder();
        if (promptMode) {
            markdown.append(replay.instructions()).append("\n\n");
        }

        markdown.append("# Task Session Replay\n\n");
        markdown.append("## Task\n");
        markdown.append("- **ID:** ").append(replay.taskId()).append('\n');
        markdown.append("- **Description:** ").append(replay.description()).append('\n');
        markdown.append("- **Tool:** ").append(replay.sourceTool()).append('\n');
        markdown.append("- **Status:** ").append(replay.status()).append('\n');
        markdown.append("- **Retries:** ").append(replay.retryCount()).append('\n');
        markdown.append("- **Agent:** ").append(replay.agentHostname())
                .append(" (").append(replay.developerEmail()).append(")\n");
        markdown.append("- **Started:** ").append(replay.startedAt()).append('\n');
        if (replay.completedAt() != null) {
            markdown.append("- **Completed:** ").append(replay.completedAt()).append('\n');
        }
        if (replay.externalSessionId() != null && !replay.externalSessionId().isBlank()) {
            markdown.append("- **Source session:** `").append(replay.externalSessionId()).append("`\n");
        }

        markdown.append("\n## Token summary\n");
        markdown.append("- Input: ").append(replay.tokenSummary().inputTokens()).append('\n');
        markdown.append("- Output: ").append(replay.tokenSummary().outputTokens()).append('\n');
        markdown.append("- Estimated cost: $")
                .append(String.format("%.4f", replay.tokenSummary().estimatedCostUsd()))
                .append('\n');

        markdown.append("\n## Timeline\n");
        int index = 1;
        for (SessionReplayEntry entry : replay.entries()) {
            markdown.append("\n### ")
                    .append(index++)
                    .append(". ")
                    .append(entry.kind());
            if (entry.retry()) {
                markdown.append(" (retry)");
            }
            markdown.append('\n');
            markdown.append("- **When:** ").append(entry.occurredAt()).append('\n');
            markdown.append("- **Summary:** ").append(entry.narrative()).append('\n');

            if (entry.details() != null && !entry.details().isEmpty()) {
                for (Map.Entry<String, Object> detail : entry.details().entrySet()) {
                    if (detail.getValue() == null) {
                        continue;
                    }
                    markdown.append("- **").append(detail.getKey()).append(":** ")
                            .append(detail.getValue()).append('\n');
                }
            }

            if (entry.tokenUsage() != null) {
                markdown.append("- **Tokens:** ")
                        .append(SessionReplayNarrator.formatTokenUsage(entry.tokenUsage()))
                        .append('\n');
            }
        }

        if (!promptMode) {
            markdown.append("\n## Agent instructions\n");
            markdown.append(replay.instructions()).append('\n');
        }

        return markdown.toString().trim();
    }

    private String buildInstructions(TaskEntity task) {
        return """
                You are receiving a structured replay of a prior coding-agent task session.
                Use it to understand what the developer asked for, which files were touched, where retries happened, \
                and how token/context pressure evolved. Continue the work without repeating failed attempts unless \
                the developer explicitly asks to retry them.
                """
                + " Task tool: " + task.getSourceTool().name()
                + ". Original request: " + task.getDescription();
    }

    public record TaskSessionReplay(
            int schemaVersion,
            UUID taskId,
            String description,
            SourceTool sourceTool,
            String status,
            int retryCount,
            String externalSessionId,
            Instant startedAt,
            Instant completedAt,
            String metadata,
            String agentHostname,
            String developerEmail,
            com.agentretry.manager.graphql.TokenUsageSummary tokenSummary,
            List<SessionReplayEntry> entries,
            String instructions
    ) {
    }

    public record SessionReplayEntry(
            String kind,
            Instant occurredAt,
            Integer sequence,
            boolean retry,
            String narrative,
            Map<String, Object> details,
            EventTokenUsage tokenUsage
    ) {
        static SessionReplayEntry fromEvent(
                TaskEventEntity event,
                EventTokenUsage tokenUsage,
                String narrative
        ) {
            return new SessionReplayEntry(
                    event.getEventType().name(),
                    event.getOccurredAt(),
                    event.getSequenceNum(),
                    event.isRetry(),
                    narrative,
                    SessionReplayNarrator.detailsFromPayload(event.getPayload()),
                    tokenUsage
            );
        }

        static SessionReplayEntry fromTokenTurn(TokenUsageEntity turn, String narrative) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("turnIndex", turn.getTurnIndex());
            details.put("model", turn.getModel());
            return new SessionReplayEntry(
                    "TOKEN_TURN",
                    turn.getRecordedAt(),
                    null,
                    false,
                    narrative,
                    details,
                    new EventTokenUsage(
                            turn.getInputTokens(),
                            turn.getOutputTokens(),
                            turn.getCacheReadTokens(),
                            turn.getCacheWriteTokens(),
                            turn.getReasoningTokens(),
                            turn.getEstimatedCostUsd() != null
                                    ? turn.getEstimatedCostUsd().doubleValue()
                                    : null,
                            turn.getModel()
                    )
            );
        }
    }
}
