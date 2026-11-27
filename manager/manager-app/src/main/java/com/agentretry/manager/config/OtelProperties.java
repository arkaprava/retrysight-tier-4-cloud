/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "manager.otel")
public class OtelProperties {
    private boolean ingestEnabled = true;
    /** When true, map Claude Cowork OTLP logs into tasks/tokens. */
    private boolean coworkMappingEnabled = true;
    /** When true, persist OTLP trace spans into the span store. */
    private boolean traceMappingEnabled = true;
    /** Max OTLP JSON body size (bytes). */
    private int maxRequestBodyBytes = 5_242_880;
    /** Max log records accepted per request. */
    private int maxLogRecords = 1000;
    /** Max spans accepted per trace request. */
    private int maxSpans = 1000;

    public boolean isIngestEnabled() { return ingestEnabled; }
    public void setIngestEnabled(boolean ingestEnabled) { this.ingestEnabled = ingestEnabled; }

    public boolean isCoworkMappingEnabled() { return coworkMappingEnabled; }
    public void setCoworkMappingEnabled(boolean coworkMappingEnabled) {
        this.coworkMappingEnabled = coworkMappingEnabled;
    }

    public boolean isTraceMappingEnabled() { return traceMappingEnabled; }
    public void setTraceMappingEnabled(boolean traceMappingEnabled) {
        this.traceMappingEnabled = traceMappingEnabled;
    }

    public int getMaxRequestBodyBytes() {
        return maxRequestBodyBytes;
    }

    public void setMaxRequestBodyBytes(int maxRequestBodyBytes) {
        this.maxRequestBodyBytes = maxRequestBodyBytes;
    }

    public int getMaxLogRecords() {
        return maxLogRecords;
    }

    public void setMaxLogRecords(int maxLogRecords) {
        this.maxLogRecords = maxLogRecords;
    }

    public int getMaxSpans() {
        return maxSpans;
    }

    public void setMaxSpans(int maxSpans) {
        this.maxSpans = maxSpans;
    }
}
