/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.contract;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ProjectContextKeys {

    public static final String PROJECT_ROOT = "projectRoot";
    public static final String REPO_NAME = "repoName";
    public static final String GIT_BRANCH = "gitBranch";
    public static final String GIT_COMMIT = "gitCommit";
    public static final String GIT_COMMIT_SHORT = "gitCommitShort";
    public static final String REMOTE_URL = "remoteUrl";
    public static final String REMOTE_HOST = "remoteHost";
    public static final String IS_DIRTY = "isDirty";
    public static final String RESOLVED_FROM = "resolvedFrom";
    public static final String IS_SUBMODULE = "isSubmodule";
    public static final String IS_WORKTREE = "isWorktree";
    public static final String REDACT_PROJECT_ROOT = "redactProjectRoot";

    private ProjectContextKeys() {
    }

    public static Map<String, Object> toMetadataMap(ProjectContext context) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        put(metadata, PROJECT_ROOT, context.projectRoot());
        put(metadata, REPO_NAME, context.repoName());
        put(metadata, GIT_BRANCH, context.gitBranch());
        put(metadata, GIT_COMMIT, context.gitCommit());
        put(metadata, GIT_COMMIT_SHORT, context.gitCommitShort());
        put(metadata, REMOTE_URL, context.remoteUrl());
        put(metadata, REMOTE_HOST, context.remoteHost());
        put(metadata, IS_DIRTY, context.isDirty());
        put(metadata, RESOLVED_FROM, context.resolvedFrom());
        put(metadata, IS_SUBMODULE, context.isSubmodule());
        put(metadata, IS_WORKTREE, context.isWorktree());
        return metadata;
    }

    public static ProjectContext fromMetadataMap(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        if (!hasGitContext(metadata)) {
            return null;
        }
        return new ProjectContext(
                stringValue(metadata.get(PROJECT_ROOT)),
                stringValue(metadata.get(REPO_NAME)),
                stringValue(metadata.get(GIT_BRANCH)),
                stringValue(metadata.get(GIT_COMMIT)),
                stringValue(metadata.get(GIT_COMMIT_SHORT)),
                stringValue(metadata.get(REMOTE_URL)),
                stringValue(metadata.get(REMOTE_HOST)),
                booleanValue(metadata.get(IS_DIRTY)),
                stringValue(metadata.get(RESOLVED_FROM)),
                booleanValue(metadata.get(IS_SUBMODULE)),
                booleanValue(metadata.get(IS_WORKTREE))
        );
    }

    public static boolean hasGitContext(Map<String, Object> metadata) {
        if (metadata == null) {
            return false;
        }
        return metadata.containsKey(PROJECT_ROOT)
                || metadata.containsKey(REPO_NAME)
                || metadata.containsKey(GIT_BRANCH)
                || metadata.containsKey(GIT_COMMIT);
    }

    public static void mergeInto(Map<String, Object> target, ProjectContext context) {
        if (context == null) {
            return;
        }
        target.putAll(toMetadataMap(context));
    }

    private static void put(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return null;
    }
}
