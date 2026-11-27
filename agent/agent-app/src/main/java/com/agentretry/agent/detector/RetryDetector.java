/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.detector;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RetryDetector {

    private final Map<String, Boolean> lastBashFailed = new ConcurrentHashMap<>();

    public boolean isFileReEdit(int priorEditCount) {
        return priorEditCount > 0;
    }

    public boolean isTestFixLoop(String sessionKey, boolean commandFailed) {
        String key = sessionKey + ":bash";
        boolean previousFailed = lastBashFailed.getOrDefault(key, false);
        lastBashFailed.put(key, commandFailed);
        return previousFailed && !commandFailed;
    }

    public void recordBashResult(String sessionKey, boolean failed) {
        lastBashFailed.put(sessionKey + ":bash", failed);
    }

    public boolean wasLastBashFailure(String sessionKey) {
        return lastBashFailed.getOrDefault(sessionKey + ":bash", false);
    }
}
