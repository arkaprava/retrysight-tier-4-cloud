/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.git;

import com.agentretry.agent.config.AgentProperties;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

@Component
public class GitProjectResolver {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final AgentProperties properties;
    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "git-project-resolver");
        thread.setDaemon(true);
        return thread;
    });

    public GitProjectResolver(AgentProperties properties) {
        this.properties = properties;
    }

    public ResolvedGitProject resolve(Path workspace) {
        if (!properties.isGitContextCaptureEnabled() || workspace == null) {
            return null;
        }

        Path normalized = workspace.toAbsolutePath().normalize();
        String cacheKey = normalized.toString();
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.value();
        }

        if (!Files.isDirectory(normalized)) {
            return null;
        }

        Path gitRoot = resolveGitRoot(normalized);
        if (gitRoot == null) {
            return null;
        }

        String branch = runGit(gitRoot, "branch", "--show-current");
        String commit = runGit(gitRoot, "rev-parse", "HEAD");
        String remoteUrl = runGit(gitRoot, "remote", "get-url", "origin");
        boolean dirty = isDirty(gitRoot);
        String repoName = gitRoot.getFileName() != null ? gitRoot.getFileName().toString() : null;
        String commitShort = commit != null && commit.length() > 8 ? commit.substring(0, 8) : commit;
        String remoteHost = extractRemoteHost(remoteUrl);
        Boolean submodule = null;
        Boolean worktree = null;

        if (properties.isGitContextAdvancedEnabled()) {
            String superProject = runGit(gitRoot, "rev-parse", "--show-superproject-working-tree");
            submodule = superProject != null && !superProject.isBlank();
            String gitDir = runGit(gitRoot, "rev-parse", "--git-dir");
            worktree = gitDir != null && gitDir.contains(".git/worktrees");
        }

        ResolvedGitProject resolved = new ResolvedGitProject(
                gitRoot.toString(),
                repoName,
                emptyToNull(branch),
                emptyToNull(commit),
                emptyToNull(commitShort),
                emptyToNull(remoteUrl),
                remoteHost,
                dirty,
                "git",
                submodule,
                worktree
        );
        cache.put(cacheKey, new CacheEntry(resolved, InstantSource.now().plus(CACHE_TTL)));
        return resolved;
    }

    private Path resolveGitRoot(Path workspace) {
        String topLevel = runGit(workspace, "rev-parse", "--show-toplevel");
        if (topLevel == null || topLevel.isBlank()) {
            return null;
        }
        return Path.of(topLevel.trim());
    }

    private boolean isDirty(Path gitRoot) {
        String status = runGit(gitRoot, "status", "--porcelain");
        return status != null && !status.isBlank();
    }

    private String runGit(Path directory, String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        for (String arg : args) {
            command.add(arg);
        }

        Future<String> future = executor.submit(() -> execute(command, directory));
        try {
            return future.get(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String execute(List<String> command, Path directory) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(directory.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String output = reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .reduce((first, second) -> first + "\n" + second)
                    .orElse("");
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return null;
            }
            return output.isBlank() ? null : output;
        }
    }

    private String extractRemoteHost(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            return null;
        }
        String trimmed = remoteUrl.trim();
        if (trimmed.contains("@") && trimmed.contains(":")) {
            int at = trimmed.indexOf('@');
            int colon = trimmed.indexOf(':', at);
            if (at >= 0 && colon > at) {
                return trimmed.substring(at + 1, colon);
            }
        }
        try {
            java.net.URI uri = java.net.URI.create(trimmed.replace("git@", "https://").replace("ssh://", "https://"));
            return uri.getHost();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record ResolvedGitProject(
            String projectRoot,
            String repoName,
            String gitBranch,
            String gitCommit,
            String gitCommitShort,
            String remoteUrl,
            String remoteHost,
            boolean isDirty,
            String resolvedFrom,
            Boolean isSubmodule,
            Boolean worktree
    ) {
    }

    private record CacheEntry(ResolvedGitProject value, java.time.Instant expiresAt) {
        boolean isExpired() {
            return InstantSource.now().isAfter(expiresAt);
        }
    }

    private static final class InstantSource {
        private InstantSource() {
        }

        static java.time.Instant now() {
            return java.time.Instant.now();
        }
    }
}
