/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.identity;

import com.agentretry.agent.config.AgentProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentIdentityStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsAndReloadsAgentId() {
        Path idFile = tempDir.resolve("agent-id");
        AgentProperties properties = new AgentProperties();
        properties.getIdentity().setIdFile(idFile.toString());

        AgentIdentityStore store = new AgentIdentityStore(properties);
        assertTrue(store.getAgentId().isEmpty());

        UUID id = UUID.randomUUID();
        store.remember(id);
        assertEquals(id, store.getAgentId().orElseThrow());

        AgentIdentityStore reloaded = new AgentIdentityStore(properties);
        assertEquals(id, reloaded.getAgentId().orElseThrow());
    }
}
