/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;

@SpringBootTest
class AgentApplicationTests {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void isolateRuntimeState(DynamicPropertyRegistry registry) {
        registry.add("agent.identity.lock-file", () -> tempDir.resolve("agent.lock").toString());
        registry.add("agent.identity.id-file", () -> tempDir.resolve("agent-id").toString());
        registry.add("agent.upload.buffer-path", () -> tempDir.resolve("buffer.db").toString());
    }

    @Test
    void contextLoads() {
    }
}
