/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.collector;

import java.nio.file.Files;
import java.nio.file.Path;

final class CursorStateDbPaths {

    private CursorStateDbPaths() {
    }

    static Path defaultStateDbPath() {
        String home = System.getProperty("user.home", "");
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            return Path.of(home, "Library/Application Support/Cursor/User/globalStorage/state.vscdb");
        }
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Path.of(appData, "Cursor/User/globalStorage/state.vscdb");
            }
            return Path.of(home, "AppData/Roaming/Cursor/User/globalStorage/state.vscdb");
        }
        return Path.of(home, ".config/Cursor/User/globalStorage/state.vscdb");
    }

    static boolean exists(Path path) {
        return path != null && Files.isRegularFile(path);
    }
}
