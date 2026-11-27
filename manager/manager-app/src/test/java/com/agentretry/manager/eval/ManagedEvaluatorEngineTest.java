/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.eval;

import com.agentretry.contract.SourceTool;
import com.agentretry.contract.TaskEventType;
import com.agentretry.contract.TaskStatus;
import com.agentretry.manager.domain.TaskEntity;
import com.agentretry.manager.domain.TaskEventEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManagedEvaluatorEngineTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void highRetryFailsWhenAboveThreshold() {
        TaskEntity task = taskWithRetries(5);
        var outcome = ManagedEvaluatorEngine.evaluate(
                "{\"evaluator\":\"HIGH_RETRY\",\"maxRetries\":3}",
                task,
                List.of(),
                List.of(),
                0,
                objectMapper
        );
        assertEquals(EvalRunLabel.FAIL, outcome.label());
    }

    @Test
    void toolFailureRatePassesWhenNoToolEvents() {
        TaskEntity task = taskWithRetries(0);
        var outcome = ManagedEvaluatorEngine.evaluate(
                "{\"evaluator\":\"TOOL_FAILURE_RATE\",\"maxFailureRate\":0.25}",
                task,
                List.of(),
                List.of(),
                0,
                objectMapper
        );
        assertEquals(EvalRunLabel.PASS, outcome.label());
    }

    @Test
    void toolFailureRateFailsOnHighRejectRate() {
        TaskEntity task = taskWithRetries(0);
        TaskEventEntity reject1 = event(task, TaskEventType.DIFF_REJECTED, true);
        TaskEventEntity reject2 = event(task, TaskEventType.TEST_FAIL, true);
        TaskEventEntity accept = event(task, TaskEventType.DIFF_ACCEPTED, false);
        var outcome = ManagedEvaluatorEngine.evaluate(
                "{\"evaluator\":\"TOOL_FAILURE_RATE\",\"maxFailureRate\":0.25}",
                task,
                List.of(reject1, reject2, accept, accept),
                List.of(),
                0,
                objectMapper
        );
        assertEquals(EvalRunLabel.FAIL, outcome.label());
    }

    private static TaskEntity taskWithRetries(int retries) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setDescription("test");
        task.setSourceTool(SourceTool.CURSOR);
        task.setStatus(TaskStatus.COMPLETED);
        task.setRetryCount(retries);
        task.setStartedAt(Instant.now());
        return task;
    }

    private static TaskEventEntity event(TaskEntity task, TaskEventType type, boolean retry) {
        TaskEventEntity event = new TaskEventEntity();
        event.setId(UUID.randomUUID());
        event.setTask(task);
        event.setEventType(type);
        event.setRetry(retry);
        event.setSequenceNum(1);
        event.setOccurredAt(Instant.now());
        return event;
    }
}
