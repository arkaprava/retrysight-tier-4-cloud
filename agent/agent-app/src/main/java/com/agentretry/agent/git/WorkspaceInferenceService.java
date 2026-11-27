/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.git;

import com.agentretry.contract.SourceTool;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class WorkspaceInferenceService {

    private final Map<SourceTool, IdeWorkspaceMapper> mappers;

    public WorkspaceInferenceService(List<IdeWorkspaceMapper> workspaceMappers) {
        this.mappers = new EnumMap<>(SourceTool.class);
        for (IdeWorkspaceMapper mapper : workspaceMappers) {
            mappers.put(mapper.sourceTool(), mapper);
        }
    }

    public Optional<Path> inferWorkspace(SourceTool sourceTool, String sessionKey, Path filePath) {
        if (filePath != null) {
            Path parent = filePath.getParent();
            if (parent != null) {
                return Optional.of(parent);
            }
        }

        if (sessionKey != null && !sessionKey.isBlank()) {
            Optional<Path> fromSession = inferFromSessionKey(sourceTool, sessionKey);
            if (fromSession.isPresent()) {
                return fromSession;
            }
        }

        return Optional.empty();
    }

    public Optional<Path> inferFromSessionKey(SourceTool sourceTool, String sessionKey) {
        IdeWorkspaceMapper mapper = mappers.get(sourceTool);
        if (mapper != null) {
            Optional<Path> mapped = mapper.mapSessionPath(sessionKey);
            if (mapped.isPresent()) {
                return mapped;
            }
        }

        return inferFromGenericSessionPath(sessionKey);
    }

    private Optional<Path> inferFromGenericSessionPath(String sessionKey) {
        String normalized = sessionKey.replace('\\', '/');
        for (String marker : List.of("/projects/", "/workspace/", "/repos/")) {
            int index = normalized.indexOf(marker);
            if (index > 0) {
                int end = normalized.indexOf('/', index + marker.length());
                String candidate = end > 0 ? normalized.substring(0, end) : normalized;
                return Optional.of(Path.of(candidate));
            }
        }
        return Optional.empty();
    }
}
