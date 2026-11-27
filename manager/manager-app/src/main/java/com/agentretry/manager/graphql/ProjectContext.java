/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

public record ProjectContext(
        String projectRoot,
        String repoName,
        String gitBranch,
        String gitCommit,
        String gitCommitShort,
        String remoteUrl,
        String remoteHost,
        Boolean isDirty,
        String resolvedFrom,
        Boolean isSubmodule,
        Boolean isWorktree
) {
}
