/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import java.time.Instant;
import java.util.List;

public record ArchivePolicy(
        boolean enabled,
        String mode,
        String scheduleCron,
        int taskEventsDays,
        int tokenUsageDays,
        int taskHeadersDays,
        List<String> datasets,
        String destinationType,
        String localPath,
        String networkPath,
        String s3Bucket,
        String s3Prefix,
        String s3Region,
        String s3Endpoint,
        String s3AccessKeyEnv,
        String s3SecretKeyEnv,
        boolean legalHold,
        Instant updatedAt,
        ArchiveCapabilities capabilities
) {
}
