/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "manager.evaluations")
public class EvalProperties {

    private boolean enabled = true;
    private boolean onlineOnComplete = true;
    private boolean autoEnqueueOnFail = true;
    private int replayExcerptMaxChars = 8000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isOnlineOnComplete() {
        return onlineOnComplete;
    }

    public void setOnlineOnComplete(boolean onlineOnComplete) {
        this.onlineOnComplete = onlineOnComplete;
    }

    public boolean isAutoEnqueueOnFail() {
        return autoEnqueueOnFail;
    }

    public void setAutoEnqueueOnFail(boolean autoEnqueueOnFail) {
        this.autoEnqueueOnFail = autoEnqueueOnFail;
    }

    public int getReplayExcerptMaxChars() {
        return replayExcerptMaxChars;
    }

    public void setReplayExcerptMaxChars(int replayExcerptMaxChars) {
        this.replayExcerptMaxChars = replayExcerptMaxChars;
    }
}
