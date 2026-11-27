/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.collector;

import com.agentretry.contract.SourceTool;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ParseHealthTracker {

    private final Map<SourceTool, AtomicLong> parseErrors = new ConcurrentHashMap<>();

    public void recordParseError(SourceTool tool) {
        parseErrors.computeIfAbsent(tool, ignored -> new AtomicLong()).incrementAndGet();
    }

    public long parseErrors(SourceTool tool) {
        AtomicLong counter = parseErrors.get(tool);
        return counter == null ? 0L : counter.get();
    }

    public Map<SourceTool, Long> snapshot() {
        Map<SourceTool, Long> out = new ConcurrentHashMap<>();
        parseErrors.forEach((tool, counter) -> out.put(tool, counter.get()));
        return Map.copyOf(out);
    }
}
