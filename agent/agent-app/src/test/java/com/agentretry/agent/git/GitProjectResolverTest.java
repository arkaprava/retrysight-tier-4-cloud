/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.git;

import com.agentretry.agent.config.AgentProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GitProjectResolverTest {

    @Test
    @EnabledIf("gitAvailable")
    void resolvesCurrentRepository() throws Exception {
        AgentProperties properties = enabledProperties();
        GitProjectResolver resolver = new GitProjectResolver(properties);

        Path workspace = Path.of("").toAbsolutePath();
        assumeTrue(Files.isDirectory(workspace.resolve(".git"))
                || Files.exists(workspace.resolve(".git")));

        GitProjectResolver.ResolvedGitProject resolved = resolver.resolve(workspace);
        assertNotNull(resolved);
        assertNotNull(resolved.gitCommit());
        assertNotNull(resolved.projectRoot());
    }

    @Test
    void skipsWhenFeatureDisabled() {
        AgentProperties properties = new AgentProperties();
        properties.getPlatform().getFeatures().setGitContextCapture(false);
        GitProjectResolver resolver = new GitProjectResolver(properties);

        GitProjectResolver.ResolvedGitProject resolved = resolver.resolve(Path.of("."));
        assertTrue(resolved == null);
    }

    @Test
    void claudeWorkspaceMapperDecodesProjectPath() {
        String encoded = "-Users-dev-Projects-sample-app";
        String decoded = ClaudeWorkspaceMapper.decodeClaudeProjectPath(encoded);
        assertTrue(decoded.contains("Users"));
        assertTrue(decoded.contains("Projects"));
    }

    static boolean gitAvailable() {
        try {
            Process process = new ProcessBuilder("git", "--version").start();
            return process.waitFor() == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private AgentProperties enabledProperties() {
        AgentProperties properties = new AgentProperties();
        properties.getPlatform().getFeatures().setGitContextCapture(true);
        properties.getPlatform().setInstallationTier(com.agentretry.contract.InstallationTier.TIER_4_CLOUD);
        return properties;
    }
}
