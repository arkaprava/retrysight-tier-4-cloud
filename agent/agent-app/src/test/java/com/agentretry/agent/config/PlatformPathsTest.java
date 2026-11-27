/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformPathsTest {

    @Test
    void expandUserPathHandlesTildePrefix() {
        String home = System.getProperty("user.home");
        Path expanded = PlatformPaths.expandUserPath("~/.cursor/projects");
        assertTrue(expanded.toString().replace('\\', '/').endsWith(".cursor/projects"));
        assertTrue(expanded.toString().startsWith(home.replace('\\', '/').substring(0, 4)));
    }

    @Test
    void defaultIdeDirsAreUnderHome() {
        String home = System.getProperty("user.home");
        assertEquals(Path.of(home, ".cursor/projects"), PlatformPaths.defaultCursorProjectsDir());
        assertEquals(Path.of(home, ".claude/projects"), PlatformPaths.defaultClaudeProjectsDir());
        assertEquals(Path.of(home, ".codex/sessions"), PlatformPaths.defaultCodexSessionsDir());
    }
}
