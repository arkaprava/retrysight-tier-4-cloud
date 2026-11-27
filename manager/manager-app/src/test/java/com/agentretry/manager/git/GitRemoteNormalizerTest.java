/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.git;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GitRemoteNormalizerTest {

    private final GitRemoteNormalizer normalizer = new GitRemoteNormalizer();

    @Test
    void normalizesHttpsGithubRemote() {
        assertEquals(
                "acme/agent-retry-platform",
                normalizer.normalizeRemoteSlug("https://github.com/acme/agent-retry-platform.git")
        );
        assertEquals("github.com", normalizer.extractRemoteHost("https://github.com/acme/agent-retry-platform.git"));
    }

    @Test
    void normalizesScpStyleRemote() {
        assertEquals(
                "acme/agent-retry-platform",
                normalizer.normalizeRemoteSlug("git@github.com:acme/agent-retry-platform.git")
        );
        assertEquals("github.com", normalizer.extractRemoteHost("git@github.com:acme/agent-retry-platform.git"));
    }

    @Test
    void returnsNullForBlankRemote() {
        assertNull(normalizer.normalizeRemoteSlug(" "));
        assertNull(normalizer.extractRemoteHost(null));
    }
}
