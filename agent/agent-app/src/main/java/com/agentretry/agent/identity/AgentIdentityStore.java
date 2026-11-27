/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.identity;

import com.agentretry.agent.config.AgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Persists the manager-assigned agent UUID so subsequent ingest calls can send {@code X-Agent-Id}.
 */
@Component
public class AgentIdentityStore {

    private static final Logger log = LoggerFactory.getLogger(AgentIdentityStore.class);

    private final Path idFile;
    private final AtomicReference<UUID> cachedId = new AtomicReference<>();

    public AgentIdentityStore(AgentProperties properties) {
        String configured = properties.getIdentity().getIdFile();
        this.idFile = Path.of(configured.replace("${user.home}", System.getProperty("user.home")));
        load();
    }

    public Optional<UUID> getAgentId() {
        return Optional.ofNullable(cachedId.get());
    }

    public void remember(UUID agentId) {
        if (agentId == null) {
            return;
        }
        UUID previous = cachedId.getAndSet(agentId);
        if (agentId.equals(previous)) {
            return;
        }
        try {
            Files.createDirectories(idFile.getParent());
            Files.writeString(idFile, agentId.toString(), StandardCharsets.UTF_8);
            log.info("Persisted agent id {} to {}", agentId, idFile);
        } catch (IOException ex) {
            log.warn("Failed to persist agent id to {}: {}", idFile, ex.getMessage());
        }
    }

    private void load() {
        try {
            if (!Files.isRegularFile(idFile)) {
                return;
            }
            String raw = Files.readString(idFile, StandardCharsets.UTF_8).trim();
            if (raw.isEmpty()) {
                return;
            }
            cachedId.set(UUID.fromString(raw));
            log.info("Loaded persisted agent id {} from {}", cachedId.get(), idFile);
        } catch (Exception ex) {
            log.warn("Ignoring unreadable agent id file {}: {}", idFile, ex.getMessage());
        }
    }
}
