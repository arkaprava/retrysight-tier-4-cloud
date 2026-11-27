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
public class ClaudeWorkspaceMapper implements IdeWorkspaceMapper {

    private static final Pattern CLAUDE_PROJECT = Pattern.compile("\\.claude/projects/([^/]+)");

    @Override
    public SourceTool sourceTool() {
        return SourceTool.CLAUDE_CODE;
    }

    @Override
    public Optional<Path> mapSessionPath(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            return Optional.empty();
        }

        String normalized = sessionKey.replace('\\', '/');
        Matcher matcher = CLAUDE_PROJECT.matcher(normalized);
        if (!matcher.find()) {
            return Optional.empty();
        }

        String encoded = matcher.group(1);
        if (encoded.isBlank()) {
            return Optional.empty();
        }

        String decoded = decodeClaudeProjectPath(encoded);
        if (decoded == null || decoded.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(Path.of(decoded));
    }

    static String decodeClaudeProjectPath(String encoded) {
        if (encoded.startsWith("-")) {
            return encoded.substring(1).replace('-', '/');
        }
        return encoded.replace('-', '/');
    }
}
