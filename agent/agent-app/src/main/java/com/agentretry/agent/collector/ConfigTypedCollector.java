/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.collector;

/** Collectors whose Spring config {@code type} differs from {@link #sourceTool()} alone. */
public interface ConfigTypedCollector extends SessionCollector {

    String configType();
}
