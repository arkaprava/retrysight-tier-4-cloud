/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.git;

import com.agentretry.contract.ProjectContextKeys;
import com.agentretry.manager.domain.TaskEntity;
import com.agentretry.manager.graphql.ProjectContext;
import com.agentretry.manager.platform.FeatureGateService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ProjectContextService {

    private final FeatureGateService featureGateService;
    private final GitRemoteNormalizer gitRemoteNormalizer;
    private final ObjectMapper objectMapper;

    public ProjectContextService(
            FeatureGateService featureGateService,
            GitRemoteNormalizer gitRemoteNormalizer,
            ObjectMapper objectMapper
    ) {
        this.featureGateService = featureGateService;
        this.gitRemoteNormalizer = gitRemoteNormalizer;
        this.objectMapper = objectMapper;
    }

    public ProjectContext resolve(TaskEntity task) {
        if (!featureGateService.isGitContextCaptureEnabled()) {
            return null;
        }

        ProjectContext context = fromEntity(task);
        if (context == null) {
            context = fromMetadata(task.getMetadata());
        }
        if (context == null) {
            return null;
        }

        return applyPrivacy(context, task.getMetadata());
    }

    public void applyDenormalizedColumns(TaskEntity task, Map<String, Object> metadata) {
        if (!featureGateService.isGitContextQueryEnabled() || metadata == null) {
            return;
        }

        String repoName = stringValue(metadata.get(ProjectContextKeys.REPO_NAME));
        String remoteUrl = stringValue(metadata.get(ProjectContextKeys.REMOTE_URL));

        if (featureGateService.isGitContextEnterpriseEnabled() && remoteUrl != null) {
            String normalized = gitRemoteNormalizer.normalizeRemoteSlug(remoteUrl);
            if (normalized != null) {
                repoName = normalized;
            }
        }

        task.setProjectRoot(stringValue(metadata.get(ProjectContextKeys.PROJECT_ROOT)));
        task.setRepoName(repoName);
        task.setGitBranch(stringValue(metadata.get(ProjectContextKeys.GIT_BRANCH)));
        task.setGitCommit(stringValue(metadata.get(ProjectContextKeys.GIT_COMMIT)));
        task.setRemoteUrl(remoteUrl);
        task.setRemoteHost(firstNonBlank(
                stringValue(metadata.get(ProjectContextKeys.REMOTE_HOST)),
                remoteUrl != null ? gitRemoteNormalizer.extractRemoteHost(remoteUrl) : null
        ));
    }

    public void backfillFromEventPayload(TaskEntity task, Map<String, Object> payload) {
        if (!featureGateService.isGitContextCaptureEnabled() || payload == null) {
            return;
        }
        if (hasDenormalizedGitContext(task)) {
            return;
        }

        Map<String, Object> gitContext = extractGitContext(payload);
        if (gitContext.isEmpty()) {
            return;
        }

        Map<String, Object> mergedMetadata = parseMetadataMap(task.getMetadata());
        mergedMetadata.putAll(gitContext);
        task.setMetadata(writeJson(mergedMetadata));
        applyDenormalizedColumns(task, mergedMetadata);
    }

    private ProjectContext fromEntity(TaskEntity task) {
        if (task.getRepoName() == null
                && task.getGitBranch() == null
                && task.getGitCommit() == null
                && task.getProjectRoot() == null) {
            return null;
        }

        return new ProjectContext(
                task.getProjectRoot(),
                task.getRepoName(),
                task.getGitBranch(),
                task.getGitCommit(),
                shortCommit(task.getGitCommit()),
                task.getRemoteUrl(),
                task.getRemoteHost(),
                null,
                null,
                null,
                null
        );
    }

    private ProjectContext fromMetadata(String metadataJson) {
        Map<String, Object> metadata = parseMetadataMap(metadataJson);
        if (!ProjectContextKeys.hasGitContext(metadata)) {
            return null;
        }

        String repoName = stringValue(metadata.get(ProjectContextKeys.REPO_NAME));
        String remoteUrl = stringValue(metadata.get(ProjectContextKeys.REMOTE_URL));
        if (featureGateService.isGitContextEnterpriseEnabled() && remoteUrl != null) {
            String normalized = gitRemoteNormalizer.normalizeRemoteSlug(remoteUrl);
            if (normalized != null) {
                repoName = normalized;
            }
        }

        return new ProjectContext(
                stringValue(metadata.get(ProjectContextKeys.PROJECT_ROOT)),
                repoName,
                stringValue(metadata.get(ProjectContextKeys.GIT_BRANCH)),
                stringValue(metadata.get(ProjectContextKeys.GIT_COMMIT)),
                stringValue(metadata.get(ProjectContextKeys.GIT_COMMIT_SHORT)),
                remoteUrl,
                firstNonBlank(
                        stringValue(metadata.get(ProjectContextKeys.REMOTE_HOST)),
                        remoteUrl != null ? gitRemoteNormalizer.extractRemoteHost(remoteUrl) : null
                ),
                booleanValue(metadata.get(ProjectContextKeys.IS_DIRTY)),
                stringValue(metadata.get(ProjectContextKeys.RESOLVED_FROM)),
                booleanValue(metadata.get(ProjectContextKeys.IS_SUBMODULE)),
                booleanValue(metadata.get(ProjectContextKeys.IS_WORKTREE))
        );
    }

    private ProjectContext applyPrivacy(ProjectContext context, String metadataJson) {
        if (!shouldRedact(metadataJson)) {
            return context;
        }
        return new ProjectContext(
                null,
                context.repoName(),
                context.gitBranch(),
                context.gitCommit(),
                context.gitCommitShort(),
                context.remoteUrl(),
                context.remoteHost(),
                context.isDirty(),
                context.resolvedFrom(),
                context.isSubmodule(),
                context.isWorktree()
        );
    }

    private boolean shouldRedact(String metadataJson) {
        if (!featureGateService.isGitContextEnterpriseEnabled()) {
            return false;
        }
        if (featureGateService.shouldRedactProjectRoot()) {
            return true;
        }
        Map<String, Object> metadata = parseMetadataMap(metadataJson);
        return Boolean.TRUE.equals(booleanValue(metadata.get(ProjectContextKeys.REDACT_PROJECT_ROOT)));
    }

    private Map<String, Object> extractGitContext(Map<String, Object> payload) {
        Map<String, Object> gitContext = new LinkedHashMap<>();
        for (String key : new String[] {
                ProjectContextKeys.PROJECT_ROOT,
                ProjectContextKeys.REPO_NAME,
                ProjectContextKeys.GIT_BRANCH,
                ProjectContextKeys.GIT_COMMIT,
                ProjectContextKeys.GIT_COMMIT_SHORT,
                ProjectContextKeys.REMOTE_URL,
                ProjectContextKeys.REMOTE_HOST,
                ProjectContextKeys.IS_DIRTY,
                ProjectContextKeys.RESOLVED_FROM,
                ProjectContextKeys.IS_SUBMODULE,
                ProjectContextKeys.IS_WORKTREE
        }) {
            if (payload.containsKey(key)) {
                gitContext.put(key, payload.get(key));
            }
        }
        return gitContext;
    }

    private boolean hasDenormalizedGitContext(TaskEntity task) {
        return task.getRepoName() != null
                || task.getGitBranch() != null
                || task.getGitCommit() != null
                || task.getProjectRoot() != null;
    }

    private Map<String, Object> parseMetadataMap(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private String writeJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize task metadata", ex);
        }
    }

    private String shortCommit(String commit) {
        if (commit == null || commit.length() <= 8) {
            return commit;
        }
        return commit.substring(0, 8);
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return null;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
