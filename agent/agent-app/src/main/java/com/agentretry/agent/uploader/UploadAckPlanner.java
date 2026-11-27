/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.uploader;

import com.agentretry.contract.IngestBatchItem;
import com.agentretry.contract.IngestBatchResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Maps manager batch responses to local buffer row ids safe to acknowledge. */
public final class UploadAckPlanner {

    private UploadAckPlanner() {
    }

    public static List<Long> idsToAcknowledge(EventBuffer.DrainBatch batch, IngestBatchResponse response) {
        if (batch == null || batch.isEmpty()) {
            return List.of();
        }
        if (response == null || response.errors() == null || response.errors().isEmpty()) {
            return batch.ids();
        }

        Set<String> failedKeys = failedIdempotencyKeys(response.errors());
        List<Long> ackIds = new ArrayList<>();
        List<IngestBatchItem> items = batch.items();
        List<Long> ids = batch.ids();
        for (int i = 0; i < items.size(); i++) {
            String key = items.get(i).idempotencyKey();
            if (!failedKeys.contains(key)) {
                ackIds.add(ids.get(i));
            }
        }
        return ackIds;
    }

    static Set<String> failedIdempotencyKeys(List<String> errors) {
        Set<String> failed = new HashSet<>();
        for (String error : errors) {
            if (error == null || error.isBlank()) {
                continue;
            }
            String trimmed = error.trim();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("taskstart ")) {
                String remainder = trimmed.substring("taskstart ".length());
                int colon = remainder.indexOf(':');
                String taskId = colon >= 0 ? remainder.substring(0, colon).trim() : remainder.trim();
                if (!taskId.isBlank()) {
                    failed.add(taskId + ":start");
                }
                continue;
            }
            int separator = trimmed.indexOf(": ");
            if (separator > 0) {
                failed.add(trimmed.substring(0, separator).trim());
            }
        }
        return failed;
    }
}
