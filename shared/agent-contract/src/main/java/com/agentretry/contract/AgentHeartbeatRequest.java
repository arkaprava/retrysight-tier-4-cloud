/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentHeartbeatRequest(
        @NotBlank String hostname,
        @NotBlank String developerEmail,
        String osUsername,
        String displayName,
        String gitEmail,
        List<SourceTool> activeCollectors,
        int queueDepth,
        Instant reportedAt,
        Boolean autoDiscovery,
        List<CollectorDiscoveryEntry> discoveryResults,
        List<ParseErrorEntry> parseErrors,
        Long bufferDroppedTotal
) {
    /**
     * Backward-compatible constructor for callers that do not yet send IDE user fields.
     */
    public AgentHeartbeatRequest(
            String hostname,
            String developerEmail,
            List<SourceTool> activeCollectors,
            int queueDepth,
            Instant reportedAt
    ) {
        this(hostname, developerEmail, null, null, null, activeCollectors, queueDepth, reportedAt,
                null, null, null, null);
    }

    public static AgentHeartbeatRequest withUserInfo(
            String hostname,
            String developerEmail,
            String osUsername,
            String displayName,
            String gitEmail,
            List<SourceTool> activeCollectors,
            int queueDepth,
            Instant reportedAt
    ) {
        return new AgentHeartbeatRequest(
                hostname,
                developerEmail,
                osUsername,
                displayName,
                gitEmail,
                activeCollectors,
                queueDepth,
                reportedAt,
                null,
                null,
                null,
                null
        );
    }

    public static AgentHeartbeatRequest withHealth(
            String hostname,
            String developerEmail,
            String osUsername,
            String displayName,
            String gitEmail,
            List<SourceTool> activeCollectors,
            int queueDepth,
            Instant reportedAt,
            Boolean autoDiscovery,
            List<CollectorDiscoveryEntry> discoveryResults,
            List<ParseErrorEntry> parseErrors,
            Long bufferDroppedTotal
    ) {
        return new AgentHeartbeatRequest(
                hostname,
                developerEmail,
                osUsername,
                displayName,
                gitEmail,
                activeCollectors,
                queueDepth,
                reportedAt,
                autoDiscovery,
                discoveryResults,
                parseErrors,
                bufferDroppedTotal
        );
    }
}
