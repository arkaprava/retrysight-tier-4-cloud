/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.contract;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Derives a stable task id from (source tool, external session id) so multiple
 * collector instances watching the same session produce the same task identity.
 */
public final class SessionTaskIds {

    private SessionTaskIds() {
    }

    public static UUID forSession(SourceTool sourceTool, String externalSessionId) {
        Objects.requireNonNull(sourceTool, "sourceTool");
        if (externalSessionId == null || externalSessionId.isBlank()) {
            throw new IllegalArgumentException("externalSessionId is required");
        }
        String material = sourceTool.name() + '\0' + externalSessionId.trim();
        return UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8));
    }
}
