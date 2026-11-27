/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

public record ArchiveCapabilities(
        boolean retentionPurge,
        boolean archiveLocal,
        boolean archiveNetwork,
        boolean archiveObjectStorage,
        boolean archiveRestore,
        boolean rollupAnalytics,
        boolean partitionMaintenance
) {
}
