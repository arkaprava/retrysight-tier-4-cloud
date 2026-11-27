/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.uploader;

import com.agentretry.agent.config.AgentProperties;
import com.agentretry.contract.SourceTool;
import com.agentretry.contract.TaskStartRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventBufferCapTest {

    @TempDir
    Path tempDir;

    @Test
    void dropsOldestWhenOverMaxRows() {
        AgentProperties properties = new AgentProperties();
        properties.getUpload().setBufferPath(tempDir.resolve("buffer.db").toString());
        properties.getUpload().setBufferMaxRows(3);
        properties.getUpload().setBufferMaxAgeHours(0);
        properties.getUpload().setBufferMaxBytes(0);

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        EventBuffer buffer = new EventBuffer(properties, mapper);
        for (int i = 0; i < 5; i++) {
            buffer.enqueueTaskStart(new TaskStartRequest(
                    UUID.randomUUID(),
                    "session-" + i,
                    SourceTool.CURSOR,
                    "task " + i,
                    Instant.now(),
                    null
            ));
        }

        assertEquals(3, buffer.queueDepth());
        assertTrue(buffer.droppedTotal() >= 2);
    }
}
