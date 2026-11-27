/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import java.time.Instant;
import java.util.UUID;

public record ArchiveRun(
        UUID id,
        Instant startedAt,
        Instant finishedAt,
        String status,
        boolean dryRun,
        String mode,
        String destinationType,
        long rowsExported,
        long rowsDeleted,
        long bytesWritten,
        String datasets,
        String errorMessage,
        String manifestSummary
) {
}
