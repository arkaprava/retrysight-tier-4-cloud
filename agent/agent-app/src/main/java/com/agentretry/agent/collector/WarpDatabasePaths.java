/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.collector;

import java.nio.file.Files;
import java.nio.file.Path;

public final class WarpDatabasePaths {

    private WarpDatabasePaths() {
    }

    public static Path defaultDatabasePath() {
        String home = System.getProperty("user.home");
        String os = System.getProperty("os.name", "").toLowerCase();

        if (os.contains("mac")) {
            return Path.of(home,
                    "Library/Group Containers/2BBY89MBSN.dev.warp/Library/Application Support/dev.warp.Warp-Stable/warp.sqlite");
        }
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Path.of(localAppData, "warp/Warp/data/warp.sqlite");
            }
            return Path.of(home, "AppData/Local/warp/Warp/data/warp.sqlite");
        }
        return Path.of(home, ".local/state/warp-terminal/warp.sqlite");
    }

    public static Path resolve(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return defaultDatabasePath();
        }
        if (configuredPath.startsWith("~/")) {
            return Path.of(System.getProperty("user.home"), configuredPath.substring(2));
        }
        return Path.of(configuredPath);
    }

    public static boolean exists(Path databasePath) {
        return databasePath != null && Files.isRegularFile(databasePath);
    }
}
