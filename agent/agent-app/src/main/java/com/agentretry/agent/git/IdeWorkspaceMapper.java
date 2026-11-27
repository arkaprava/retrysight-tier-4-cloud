/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.git;

import com.agentretry.contract.SourceTool;

import java.nio.file.Path;
import java.util.Optional;

public interface IdeWorkspaceMapper {

    SourceTool sourceTool();

    Optional<Path> mapSessionPath(String sessionKey);
}
