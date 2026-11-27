/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.git;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GitRemoteNormalizer {

    private static final Pattern SCP_PATTERN = Pattern.compile("^[^@]+@([^:]+):(.+)$");

    public String normalizeRemoteSlug(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            return null;
        }

        String trimmed = remoteUrl.trim();
        Matcher scp = SCP_PATTERN.matcher(trimmed);
        if (scp.matches()) {
            return stripGitSuffix(scp.group(2));
        }

        try {
            URI uri = URI.create(trimmed.replace("git@", "https://").replace("ssh://", "https://"));
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return null;
            }
            return stripGitSuffix(path.startsWith("/") ? path.substring(1) : path);
        } catch (Exception ignored) {
            return stripGitSuffix(trimmed);
        }
    }

    public String extractRemoteHost(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            return null;
        }

        String trimmed = remoteUrl.trim();
        Matcher scp = SCP_PATTERN.matcher(trimmed);
        if (scp.matches()) {
            return scp.group(1);
        }

        try {
            URI uri = URI.create(trimmed.replace("git@", "https://").replace("ssh://", "https://"));
            return uri.getHost();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String stripGitSuffix(String value) {
        if (value == null) {
            return null;
        }
        String slug = value.trim();
        if (slug.endsWith(".git")) {
            slug = slug.substring(0, slug.length() - 4);
        }
        while (slug.endsWith("/")) {
            slug = slug.substring(0, slug.length() - 1);
        }
        return slug.isEmpty() ? null : slug;
    }
}
