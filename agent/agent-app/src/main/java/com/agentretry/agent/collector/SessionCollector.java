/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.collector;

import com.agentretry.contract.SourceTool;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public interface SessionCollector {

    SourceTool sourceTool();

    boolean isEnabled();

    List<Path> watchPaths();

    void scan();

    default void expireIdleSessions(Duration idleThreshold) {
    }
}
