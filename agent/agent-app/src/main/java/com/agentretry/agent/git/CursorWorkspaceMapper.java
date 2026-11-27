/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.git;

import com.agentretry.contract.SourceTool;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CursorWorkspaceMapper implements IdeWorkspaceMapper {

    private static final Pattern CURSOR_PROJECT = Pattern.compile("\\.cursor/projects/([^/]+)");

    @Override
    public SourceTool sourceTool() {
        return SourceTool.CURSOR;
    }

    @Override
    public Optional<Path> mapSessionPath(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            return Optional.empty();
        }

        String normalized = sessionKey.replace('\\', '/');
        Matcher matcher = CURSOR_PROJECT.matcher(normalized);
        if (!matcher.find()) {
            return Optional.empty();
        }

        int start = normalized.indexOf(".cursor/projects/");
        if (start < 0) {
            return Optional.empty();
        }
        int segmentStart = start + ".cursor/projects/".length();
        int segmentEnd = normalized.indexOf('/', segmentStart);
        if (segmentEnd < 0) {
            return Optional.empty();
        }

        return Optional.of(Path.of(normalized.substring(0, segmentEnd)));
    }
}
