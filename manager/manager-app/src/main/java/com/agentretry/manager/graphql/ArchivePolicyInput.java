/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import java.util.List;

public record ArchivePolicyInput(
        Boolean enabled,
        String mode,
        String scheduleCron,
        Integer taskEventsDays,
        Integer tokenUsageDays,
        Integer taskHeadersDays,
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
        Boolean legalHold
) {
}
