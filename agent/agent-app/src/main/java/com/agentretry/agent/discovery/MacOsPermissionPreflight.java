/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.discovery;

import com.agentretry.agent.config.PlatformPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/** Best-effort macOS permission preflight for IDE log directories. */
@Component
public class MacOsPermissionPreflight {

    private static final Logger log = LoggerFactory.getLogger(MacOsPermissionPreflight.class);

    @EventListener(ApplicationReadyEvent.class)
    public void check() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("mac")) {
            return;
        }

        Path cursorState = Path.of(System.getProperty("user.home", ""),
                "Library/Application Support/Cursor/User/globalStorage/state.vscdb");
        Path cursorProjects = PlatformPaths.defaultCursorProjectsDir();

        warnIfUnreadable(cursorState, "Cursor state database");
        warnIfUnreadable(cursorProjects, "Cursor project logs");
    }

    private void warnIfUnreadable(Path path, String label) {
        if (!Files.exists(path)) {
            return;
        }
        try {
            if (Files.isDirectory(path)) {
                try (var stream = Files.newDirectoryStream(path)) {
                    stream.iterator().hasNext();
                }
            } else if (!Files.isReadable(path)) {
                log.warn("{} may be unreadable at {} — grant Full Disk Access to the RetrySight agent if collection fails",
                        label, path);
            }
        } catch (SecurityException ex) {
            log.warn("{} blocked by macOS privacy controls at {} — grant Full Disk Access to RetrySight",
                    label, path);
        } catch (Exception ex) {
            log.debug("Preflight check failed for {}: {}", path, ex.getMessage());
        }
    }
}
