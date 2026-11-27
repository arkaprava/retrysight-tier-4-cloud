/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.eval;

import com.agentretry.contract.TaskEventType;
import com.agentretry.manager.domain.TaskEntity;
import com.agentretry.manager.domain.TaskEventEntity;
import com.agentretry.manager.domain.TokenUsageEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Locale;

public final class ManagedEvaluatorEngine {

    private ManagedEvaluatorEngine() {
    }

    public static EvalOutcome evaluate(
            String ruleConfigJson,
            TaskEntity task,
            List<TaskEventEntity> events,
            List<TokenUsageEntity> tokenTurns,
            double medianTaskCostUsd,
            ObjectMapper objectMapper
    ) {
        JsonNode config = parseConfig(ruleConfigJson, objectMapper);
        String evaluator = config.path("evaluator").asText("").toUpperCase(Locale.ROOT);
        return switch (evaluator) {
            case "HIGH_RETRY" -> highRetry(task, config);
            case "TOOL_FAILURE_RATE" -> toolFailureRate(events, config);
            case "COST_OUTLIER" -> costOutlier(task, tokenTurns, medianTaskCostUsd, config);
            default -> EvalOutcome.skipped("Unknown managed evaluator: " + evaluator);
        };
    }

    private static EvalOutcome highRetry(TaskEntity task, JsonNode config) {
        int maxRetries = config.path("maxRetries").asInt(3);
        int retries = task.getRetryCount();
        if (retries <= maxRetries) {
            return EvalOutcome.pass(
                    100 - Math.min(100, retries * 10.0),
                    "Retry count " + retries + " is within threshold (" + maxRetries + ")."
            );
        }
        return EvalOutcome.fail(
                Math.max(0, 100 - retries * 15.0),
                "Retry count " + retries + " exceeds threshold of " + maxRetries + "."
        );
    }

    private static EvalOutcome toolFailureRate(List<TaskEventEntity> events, JsonNode config) {
        double maxRate = config.path("maxFailureRate").asDouble(0.25);
        int toolEvents = 0;
        int failures = 0;
        for (TaskEventEntity event : events) {
            TaskEventType type = event.getEventType();
            if (type == TaskEventType.EDIT || type == TaskEventType.DIFF_ACCEPTED
                    || type == TaskEventType.DIFF_REJECTED || type == TaskEventType.TEST_FAIL) {
                toolEvents++;
                if (type == TaskEventType.TEST_FAIL || type == TaskEventType.DIFF_REJECTED || event.isRetry()) {
                    failures++;
                }
            }
        }
        if (toolEvents == 0) {
            return EvalOutcome.pass(100, "No tool/edit events recorded for this session.");
        }
        double rate = (double) failures / toolEvents;
        if (rate <= maxRate) {
            return EvalOutcome.pass(
                    100 - rate * 100,
                    String.format(Locale.US, "Tool failure rate %.1f%% (%d/%d) is within %.0f%%.",
                            rate * 100, failures, toolEvents, maxRate * 100)
            );
        }
        return EvalOutcome.fail(
                Math.max(0, 100 - rate * 100),
                String.format(Locale.US, "Tool failure rate %.1f%% (%d/%d) exceeds %.0f%%.",
                        rate * 100, failures, toolEvents, maxRate * 100)
        );
    }

    private static EvalOutcome costOutlier(
            TaskEntity task,
            List<TokenUsageEntity> tokenTurns,
            double medianTaskCostUsd,
            JsonNode config
    ) {
        double multiplier = config.path("multiplier").asDouble(3.0);
        double minCostUsd = config.path("minCostUsd").asDouble(0.01);
        double taskCost = tokenTurns.stream()
                .mapToDouble(t -> t.getEstimatedCostUsd() != null ? t.getEstimatedCostUsd().doubleValue() : 0)
                .sum();
        if (taskCost < minCostUsd) {
            return EvalOutcome.pass(100, "Session cost below minimum evaluation threshold.");
        }
        double baseline = Math.max(minCostUsd, medianTaskCostUsd);
        if (baseline <= 0) {
            return EvalOutcome.pass(100, "Insufficient fleet cost baseline for outlier detection.");
        }
        if (taskCost <= baseline * multiplier) {
            return EvalOutcome.pass(
                    100,
                    String.format(Locale.US, "Cost $%.4f is within %.1fx fleet median ($%.4f).",
                            taskCost, multiplier, baseline)
            );
        }
        return EvalOutcome.warn(
                Math.max(0, 100 - (taskCost / baseline) * 10),
                String.format(Locale.US, "Cost $%.4f exceeds %.1fx fleet median ($%.4f).",
                        taskCost, multiplier, baseline)
        );
    }

    private static JsonNode parseConfig(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    public record EvalOutcome(EvalRunLabel label, double score, String explanation, boolean skipped) {
        public static EvalOutcome pass(double score, String explanation) {
            return new EvalOutcome(EvalRunLabel.PASS, score, explanation, false);
        }

        public static EvalOutcome fail(double score, String explanation) {
            return new EvalOutcome(EvalRunLabel.FAIL, score, explanation, false);
        }

        public static EvalOutcome warn(double score, String explanation) {
            return new EvalOutcome(EvalRunLabel.WARN, score, explanation, false);
        }

        public static EvalOutcome skipped(String explanation) {
            return new EvalOutcome(null, 0, explanation, true);
        }
    }
}
