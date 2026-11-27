/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.archive;

import java.time.Instant;
import java.util.List;

public record ArchiveManifest(
        String schemaVersion,
        String dataset,
        String day,
        long rowCount,
        String checksumSha256,
        long bytes,
        Instant exportedAt,
        List<String> files
) {
    public static final String SCHEMA_VERSION = "1";
}
