/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.git;

import com.agentretry.agent.config.AgentProperties;
import com.agentretry.agent.uploader.EventBuffer;
import com.agentretry.contract.ProjectContext;
import com.agentretry.contract.ProjectContextKeys;
import com.agentretry.contract.SourceTool;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProjectContextEnricher {

    private final AgentProperties properties;
    private final WorkspaceInferenceService workspaceInferenceService;
    private final GitProjectResolver gitProjectResolver;
    private final EventBuffer eventBuffer;
    private final Map<String, Map<String, Object>> sessionMetadata = new ConcurrentHashMap<>();

    public ProjectContextEnricher(
            AgentProperties properties,
            WorkspaceInferenceService workspaceInferenceService,
            GitProjectResolver gitProjectResolver,
            EventBuffer eventBuffer
    ) {
        this.properties = properties;
        this.workspaceInferenceService = workspaceInferenceService;
        this.gitProjectResolver = gitProjectResolver;
        this.eventBuffer = eventBuffer;
    }

    public Map<String, Object> enrichForTaskStart(
            SourceTool sourceTool,
            String sessionKey,
            Map<String, Object> metadata
    ) {
        Map<String, Object> enriched = new HashMap<>(metadata);
        if (!properties.isGitContextCaptureEnabled()) {
            return enriched;
        }

        applyPrivacyFlag(enriched);
        ProjectContext context = resolveContext(sourceTool, sessionKey, null, "session");
        if (context != null) {
            ProjectContextKeys.mergeInto(enriched, context);
        }

        sessionMetadata.put(sessionKey, enriched);
        return enriched;
    }

    public void backfillFromEdit(
            SourceTool sourceTool,
            String sessionKey,
            UUID taskId,
            String filePath,
            Map<String, Object> metadata
    ) {
        if (!properties.isGitContextCaptureEnabled() || ProjectContextKeys.hasGitContext(metadata)) {
            return;
        }

        Map<String, Object> enriched = new HashMap<>(metadata);
        applyPrivacyFlag(enriched);
        Path path = filePath != null ? Path.of(filePath) : null;
        ProjectContext context = resolveContext(sourceTool, sessionKey, path, "edit");
        if (context == null) {
            return;
        }

        ProjectContextKeys.mergeInto(enriched, context);
        sessionMetadata.put(sessionKey, enriched);
        eventBuffer.updateTaskStartMetadata(taskId, enriched);

        Map<String, Object> editPayload = new HashMap<>(ProjectContextKeys.toMetadataMap(context));
        editPayload.put("_gitBackfill", true);
        metadata.putAll(editPayload);
    }

    public Map<String, Object> sessionMetadata(String sessionKey) {
        return sessionMetadata.getOrDefault(sessionKey, Map.of());
    }

    private ProjectContext resolveContext(
            SourceTool sourceTool,
            String sessionKey,
            Path filePath,
            String resolvedFrom
    ) {
        Optional<Path> workspace = workspaceInferenceService.inferWorkspace(sourceTool, sessionKey, filePath);
        if (workspace.isEmpty()) {
            return null;
        }

        GitProjectResolver.ResolvedGitProject resolved = gitProjectResolver.resolve(workspace.get());
        if (resolved == null) {
            return null;
        }

        return new ProjectContext(
                resolved.projectRoot(),
                resolved.repoName(),
                resolved.gitBranch(),
                resolved.gitCommit(),
                resolved.gitCommitShort(),
                resolved.remoteUrl(),
                resolved.remoteHost(),
                resolved.isDirty(),
                resolvedFrom,
                resolved.isSubmodule(),
                resolved.worktree()
        );
    }

    private void applyPrivacyFlag(Map<String, Object> metadata) {
        if (properties.isGitContextEnterpriseEnabled() && !properties.getPrivacy().isStoreFullPrompts()) {
            metadata.put(ProjectContextKeys.REDACT_PROJECT_ROOT, true);
        }
    }
}
