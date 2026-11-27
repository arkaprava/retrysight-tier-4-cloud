/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.identity;

import com.agentretry.agent.config.AgentProperties;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
public class IdeUserInfoResolver {

    private final AgentProperties properties;

    public IdeUserInfoResolver(AgentProperties properties) {
        this.properties = properties;
    }

    public ResolvedUser resolve() {
        String osUsername = resolveOsUsername();
        String gitName = readGitConfig("user.name");
        String gitEmail = readGitConfig("user.email");
        String displayName = firstNonBlank(
                properties.getUser().getDisplayName(),
                gitName,
                osUsername
        );
        String developerEmail = firstNonBlank(
                properties.getUser().getEmail(),
                gitEmail,
                osUsername + "@local"
        );

        return new ResolvedUser(osUsername, displayName, gitEmail, developerEmail);
    }

    private String resolveOsUsername() {
        String configured = properties.getUser().getOsUsername();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return System.getProperty("user.name", "developer");
    }

    private String readGitConfig(String key) {
        String value = runGitConfig("git", "config", key);
        if (value != null) {
            return value;
        }
        return runGitConfig("git", "config", "--global", key);
    }

    private String runGitConfig(String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) {
                return null;
            }
            try (InputStream input = process.getInputStream()) {
                String output = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
                return output.isBlank() ? null : output;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    public record ResolvedUser(
            String osUsername,
            String displayName,
            String gitEmail,
            String developerEmail
    ) {
    }
}
