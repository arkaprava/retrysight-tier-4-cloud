/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "manager.chat")
public class ChatProperties {

    private boolean enabled = true;
    private String ollamaBaseUrl = "http://127.0.0.1:11434";
    private String model = "llama3.2";
    private double temperature = 0.2;
    private int maxToolRounds = 4;
    private int maxHistoryMessages = 20;
    private int requestsPerMinute = 30;
    private int maxToolResultChars = 12000;
    private boolean allowDeterministicFallback = true;
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 120000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getOllamaBaseUrl() { return ollamaBaseUrl; }
    public void setOllamaBaseUrl(String ollamaBaseUrl) { this.ollamaBaseUrl = ollamaBaseUrl; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public int getMaxToolRounds() { return maxToolRounds; }
    public void setMaxToolRounds(int maxToolRounds) { this.maxToolRounds = maxToolRounds; }
    public int getMaxHistoryMessages() { return maxHistoryMessages; }
    public void setMaxHistoryMessages(int maxHistoryMessages) { this.maxHistoryMessages = maxHistoryMessages; }
    public int getRequestsPerMinute() { return requestsPerMinute; }
    public void setRequestsPerMinute(int requestsPerMinute) { this.requestsPerMinute = requestsPerMinute; }
    public int getMaxToolResultChars() { return maxToolResultChars; }
    public void setMaxToolResultChars(int maxToolResultChars) { this.maxToolResultChars = maxToolResultChars; }
    public boolean isAllowDeterministicFallback() { return allowDeterministicFallback; }
    public void setAllowDeterministicFallback(boolean allowDeterministicFallback) {
        this.allowDeterministicFallback = allowDeterministicFallback;
    }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
}
