/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.config;

import com.agentretry.contract.SourceToolDescriptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Cross-platform home and IDE log directory resolution (Windows, macOS, Linux, WSL). */
public final class PlatformPaths {

    private PlatformPaths() {
    }

    public static Path expandUserPath(String path) {
        if (path == null || path.isBlank()) {
            return Path.of("");
        }
        String normalized = path.trim();
        String home = System.getProperty("user.home", "");
        if (normalized.startsWith("~/")) {
            return Path.of(home, normalized.substring(2));
        }
        if (normalized.equals("~")) {
            return Path.of(home);
        }
        if (normalized.startsWith("~")) {
            return Path.of(home, normalized.substring(1));
        }
        return Path.of(normalized);
    }

    public static Path defaultCursorProjectsDir() {
        return expandUserPath("~/.cursor/projects");
    }

    public static Path defaultClaudeProjectsDir() {
        return expandUserPath("~/.claude/projects");
    }

    public static Path defaultCodexSessionsDir() {
        return expandUserPath("~/.codex/sessions");
    }

    public static boolean isWsl() {
        if (System.getenv("WSL_DISTRO_NAME") != null) {
            return true;
        }
        return Files.exists(Path.of("/proc/sys/fs/binfmt_misc/WSLInterop"));
    }

    public static Optional<Path> resolveWatchPath(SourceToolDescriptor descriptor) {
        if (descriptor == null || descriptor.defaultWatchPath() == null) {
            return Optional.empty();
        }
        if (descriptor.defaultWatchPath().startsWith("otel://")) {
            return Optional.empty();
        }
        return Optional.of(expandUserPath(descriptor.defaultWatchPath()));
    }

    public static List<Path> candidateWatchPaths(SourceToolDescriptor descriptor) {
        List<Path> candidates = new ArrayList<>();
        resolveWatchPath(descriptor).ifPresent(candidates::add);

        if ("cursor".equalsIgnoreCase(descriptor.configType())) {
            candidates.add(defaultCursorProjectsDir());
            wslWindowsHome().map(home -> home.resolve(".cursor/projects")).ifPresent(candidates::add);
        } else if ("claude_code".equalsIgnoreCase(descriptor.configType())) {
            candidates.add(defaultClaudeProjectsDir());
            wslWindowsHome().map(home -> home.resolve(".claude/projects")).ifPresent(candidates::add);
        } else if ("codex".equalsIgnoreCase(descriptor.configType())) {
            candidates.add(defaultCodexSessionsDir());
            wslWindowsHome().map(home -> home.resolve(".codex/sessions")).ifPresent(candidates::add);
        } else if ("kiro".equalsIgnoreCase(descriptor.configType())) {
            candidates.add(expandUserPath("~/.kiro/sessions/cli"));
        } else if ("copilot".equalsIgnoreCase(descriptor.configType())) {
            candidates.add(expandUserPath("~/.copilot/session-state"));
        } else if ("warp".equalsIgnoreCase(descriptor.configType())) {
            candidates.add(resolveWarpDatabasePath());
        } else if ("kiro_ide".equalsIgnoreCase(descriptor.configType())) {
            String home = System.getProperty("user.home", "");
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("mac")) {
                candidates.add(Path.of(home, "Library/Application Support/Kiro/User/globalStorage/kiro.kiroagent"));
            } else if (os.contains("win")) {
                String appData = System.getenv("APPDATA");
                if (appData != null && !appData.isBlank()) {
                    candidates.add(Path.of(appData, "Kiro/User/globalStorage/kiro.kiroagent"));
                }
            } else {
                candidates.add(Path.of(home, ".config/Kiro/User/globalStorage/kiro.kiroagent"));
            }
        }

        return distinctExistingParents(candidates);
    }

    public static Path resolveWarpDatabasePath() {
        String home = System.getProperty("user.home", "");
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return Path.of(home,
                    "Library/Group Containers/2BBY89MBSN.dev.warp/Library/Application Support/dev.warp.Warp-Stable/warp.sqlite");
        }
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Path.of(localAppData, "dev.warp.Warp-Stable/warp.sqlite");
            }
        }
        return Path.of(home, ".warp/warp.sqlite");
    }

    public static Optional<Path> wslWindowsHome() {
        if (!isWsl()) {
            return Optional.empty();
        }
        Optional<Path> fromCmd = windowsUserProfileViaCmd();
        if (fromCmd.isPresent()) {
            return fromCmd;
        }
        String user = System.getProperty("user.name", "");
        if (!user.isBlank()) {
            Path fallback = Path.of("/mnt/c/Users", user);
            if (Files.isDirectory(fallback)) {
                return Optional.of(fallback);
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> windowsUserProfileViaCmd() {
        try {
            Process process = new ProcessBuilder("cmd.exe", "/c", "echo %USERPROFILE%")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            if (process.exitValue() != 0) {
                return Optional.empty();
            }
            String output = new String(process.getInputStream().readAllBytes()).trim();
            if (output.isBlank() || output.contains("%USERPROFILE%")) {
                return Optional.empty();
            }
            Path profile = Path.of(output.replace('\\', '/'));
            if (profile.toString().startsWith("/mnt/")) {
                return Optional.of(profile);
            }
            if (output.matches("(?i)[A-Z]:\\\\.*")) {
                String drive = output.substring(0, 1).toLowerCase(Locale.ROOT);
                String remainder = output.substring(2).replace('\\', '/');
                return Optional.of(Path.of("/mnt/" + drive + remainder));
            }
            return Files.isDirectory(profile) ? Optional.of(profile) : Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static List<Path> distinctExistingParents(List<Path> paths) {
        List<Path> distinct = new ArrayList<>();
        for (Path path : paths) {
            if (path == null) {
                continue;
            }
            Path normalized = path.normalize();
            if (distinct.stream().noneMatch(existing -> existing.equals(normalized))) {
                distinct.add(normalized);
            }
        }
        return distinct;
    }

    public static Instant newestLogTimestamp(Path root, SourceToolDescriptor.LogFormat format, int maxDepth) {
        if (root == null || !Files.exists(root)) {
            return null;
        }
        try {
            if (format == SourceToolDescriptor.LogFormat.SQLITE && Files.isRegularFile(root)) {
                return Files.getLastModifiedTime(root).toInstant();
            }
            if (!Files.isDirectory(root)) {
                return null;
            }
            Instant newest = null;
            try (Stream<Path> stream = Files.walk(root, maxDepth)) {
                for (Path path : stream.filter(Files::isRegularFile).toList()) {
                    if (!matchesFormat(path, format)) {
                        continue;
                    }
                    Instant modified = Files.getLastModifiedTime(path).toInstant();
                    if (newest == null || modified.isAfter(newest)) {
                        newest = modified;
                    }
                }
            }
            return newest;
        } catch (IOException ignored) {
            return null;
        }
    }

    public static boolean hasReadableLogSignal(Path root, SourceToolDescriptor.LogFormat format, int maxDepth) {
        if (root == null || !Files.exists(root)) {
            return false;
        }
        try {
            if (format == SourceToolDescriptor.LogFormat.SQLITE) {
                return Files.isRegularFile(root) && Files.isReadable(root);
            }
            if (!Files.isDirectory(root) || !Files.isReadable(root)) {
                return false;
            }
            try (Stream<Path> stream = Files.walk(root, maxDepth)) {
                return stream.anyMatch(path -> Files.isRegularFile(path) && matchesFormat(path, format));
            }
        } catch (IOException ignored) {
            return false;
        }
    }

    public static Optional<Path> selectBestCandidate(
            List<Path> candidates,
            SourceToolDescriptor.LogFormat format,
            int maxDepth
    ) {
        return candidates.stream()
                .filter(path -> hasReadableLogSignal(path, format, maxDepth))
                .max(Comparator.comparing(path -> {
                    Instant newest = newestLogTimestamp(path, format, maxDepth);
                    return newest == null ? Instant.EPOCH : newest;
                }));
    }

    private static boolean matchesFormat(Path path, SourceToolDescriptor.LogFormat format) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return switch (format) {
            case JSONL, EVENTS_JSONL -> name.endsWith(".jsonl");
            case SQLITE -> name.endsWith(".sqlite") || name.endsWith(".db");
            case JSON_DOC -> name.endsWith(".json");
            default -> false;
        };
    }
}
