/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.manager.domain.TaskEventEntity;
import com.agentretry.manager.domain.TokenUsageEntity;
import com.agentretry.manager.graphql.EventTokenUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class EventTokenUsageMatcher {

    /**
     * Token usage attributed to an event, with a key used to dedupe when multiple retry events
     * map to the same token turn.
     */
    public record AttributedTokenUsage(EventTokenUsage usage, String dedupeKey) {
    }

    private static final Duration MAX_TIME_DELTA = Duration.ofSeconds(30);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private EventTokenUsageMatcher() {
    }

    static EventTokenUsage match(
            TaskEventEntity event,
            List<TokenUsageEntity> tokenTurns,
            ModelPricingService pricingService
    ) {
        return attributeUsage(event, tokenTurns, pricingService)
                .map(AttributedTokenUsage::usage)
                .orElse(null);
    }

    /**
     * Links an event to token usage for cost attribution.
     * <p>
     * Dedupe keys:
     * <ul>
     *   <li>{@code payload:<eventId>} — tokens embedded in the event payload</li>
     *   <li>{@code turn:<taskId>:<turnIndex>} — matched to a recorded token turn</li>
     * </ul>
     */
    public static Optional<AttributedTokenUsage> attributeUsage(
            TaskEventEntity event,
            List<TokenUsageEntity> tokenTurns,
            ModelPricingService pricingService
    ) {
        EventTokenUsage fromPayload = parseTokensFromPayload(event.getPayload());
        if (fromPayload != null) {
            double cost = resolveCostUsd(fromPayload, pricingService);
            EventTokenUsage withCost = withEstimatedCost(fromPayload, cost);
            return Optional.of(new AttributedTokenUsage(withCost, "payload:" + event.getId()));
        }

        JsonNode payload = parsePayload(event.getPayload());
        if (payload != null && payload.has("turn")) {
            int turn = payload.get("turn").asInt();
            Optional<TokenUsageEntity> byTurn = tokenTurns.stream()
                    .filter(token -> token.getTurnIndex() == turn)
                    .findFirst();
            if (byTurn.isPresent()) {
                return Optional.of(toAttributedUsage(event.getTask().getId(), byTurn.get(), pricingService));
            }
        }

        return findNearestByTime(event.getOccurredAt(), tokenTurns)
                .map(token -> toAttributedUsage(event.getTask().getId(), token, pricingService));
    }

    private static AttributedTokenUsage toAttributedUsage(
            UUID taskId,
            TokenUsageEntity token,
            ModelPricingService pricingService
    ) {
        return new AttributedTokenUsage(
                toEventTokenUsage(token, pricingService),
                "turn:" + taskId + ":" + token.getTurnIndex()
        );
    }

    private static EventTokenUsage withEstimatedCost(EventTokenUsage usage, double cost) {
        return new EventTokenUsage(
                usage.inputTokens(),
                usage.outputTokens(),
                usage.cacheReadTokens(),
                usage.cacheWriteTokens(),
                usage.reasoningTokens(),
                cost,
                usage.model()
        );
    }

    private static double resolveCostUsd(EventTokenUsage usage, ModelPricingService pricingService) {
        if (usage.estimatedCostUsd() != null && usage.estimatedCostUsd() > 0) {
            return usage.estimatedCostUsd();
        }
        return pricingService.estimateCost(
                null,
                usage.inputTokens(),
                usage.outputTokens(),
                usage.cacheReadTokens(),
                usage.cacheWriteTokens(),
                usage.reasoningTokens()
        ).doubleValue();
    }

    private static EventTokenUsage parseTokensFromPayload(String payloadJson) {
        JsonNode payload = parsePayload(payloadJson);
        if (payload == null) {
            return null;
        }

        long input = firstLong(payload, "inputTokens", "input_tokens");
        long output = firstLong(payload, "outputTokens", "output_tokens");
        long cacheRead = firstLong(payload, "cacheReadTokens", "cache_read_tokens", "cached_input_tokens");
        long cacheWrite = firstLong(payload, "cacheWriteTokens", "cache_write_tokens");
        long reasoning = firstLong(payload, "reasoningTokens", "reasoning_tokens", "reasoning_output_tokens");

        if (input == 0 && output == 0 && cacheRead == 0 && cacheWrite == 0 && reasoning == 0) {
            return null;
        }

        String model = firstText(payload, "model");
        return new EventTokenUsage(input, output, cacheRead, cacheWrite, reasoning, null, model);
    }

    private static Optional<TokenUsageEntity> findNearestByTime(
            Instant occurredAt,
            List<TokenUsageEntity> tokenTurns
    ) {
        return tokenTurns.stream()
                .min(Comparator.comparingLong(token -> Math.abs(
                        Duration.between(token.getRecordedAt(), occurredAt).toMillis())))
                .filter(token -> {
                    long deltaMs = Math.abs(Duration.between(token.getRecordedAt(), occurredAt).toMillis());
                    return deltaMs <= MAX_TIME_DELTA.toMillis();
                });
    }

    private static EventTokenUsage toEventTokenUsage(TokenUsageEntity usage, ModelPricingService pricingService) {
        double cost = resolveCostUsd(usage, pricingService);
        return new EventTokenUsage(
                usage.getInputTokens(),
                usage.getOutputTokens(),
                usage.getCacheReadTokens(),
                usage.getCacheWriteTokens(),
                usage.getReasoningTokens(),
                cost,
                usage.getModel()
        );
    }

    private static double resolveCostUsd(TokenUsageEntity usage, ModelPricingService pricingService) {
        if (usage.getEstimatedCostUsd() != null && usage.getEstimatedCostUsd().signum() > 0) {
            return usage.getEstimatedCostUsd().doubleValue();
        }
        return pricingService.estimateCost(
                usage.getModel(),
                usage.getInputTokens(),
                usage.getOutputTokens(),
                usage.getCacheReadTokens(),
                usage.getCacheWriteTokens(),
                usage.getReasoningTokens()
        ).doubleValue();
    }

    private static JsonNode parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(payloadJson);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long firstLong(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field) && node.get(field).isNumber()) {
                return node.get(field).asLong();
            }
        }
        return 0;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field) && node.get(field).isTextual()) {
                String value = node.get(field).asText();
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }
}
