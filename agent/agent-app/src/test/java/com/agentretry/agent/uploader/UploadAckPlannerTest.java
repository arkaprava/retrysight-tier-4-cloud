/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.uploader;

import com.agentretry.contract.IngestBatchItem;
import com.agentretry.contract.IngestBatchResponse;
import com.agentretry.contract.IngestItemType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UploadAckPlannerTest {

    @Test
    void acknowledgesAllWhenNoErrors() {
        EventBuffer.DrainBatch batch = new EventBuffer.DrainBatch(
                List.of(
                        new IngestBatchItem("task-1:start", IngestItemType.TASK_START, "{}"),
                        new IngestBatchItem("task-1:event:1", IngestItemType.TASK_EVENT, "{}")
                ),
                List.of(1L, 2L)
        );
        IngestBatchResponse response = new IngestBatchResponse(2, 0, List.of(), null);

        assertEquals(List.of(1L, 2L), UploadAckPlanner.idsToAcknowledge(batch, response));
    }

    @Test
    void retainsFailedItemsForRetry() {
        EventBuffer.DrainBatch batch = new EventBuffer.DrainBatch(
                List.of(
                        new IngestBatchItem("task-1:start", IngestItemType.TASK_START, "{}"),
                        new IngestBatchItem("task-1:event:1", IngestItemType.TASK_EVENT, "{}")
                ),
                List.of(10L, 11L)
        );
        IngestBatchResponse response = new IngestBatchResponse(
                1, 0, List.of("task-1:event:1: duplicate"), null
        );

        assertEquals(List.of(10L), UploadAckPlanner.idsToAcknowledge(batch, response));
    }

    @Test
    void mapsTaskStartErrorsToStartKey() {
        var failed = UploadAckPlanner.failedIdempotencyKeys(
                List.of("taskStart 550e8400-e29b-41d4-a716-446655440000: already exists")
        );
        assertEquals(1, failed.size());
        assertEquals("550e8400-e29b-41d4-a716-446655440000:start", failed.iterator().next());
    }
}
