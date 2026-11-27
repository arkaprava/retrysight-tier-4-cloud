/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "manager.analytics")
public class AnalyticsProperties {

    /** Max inclusive day span for dashboard/cost queries (clamped server-side). */
    private int maxQueryRangeDays = 90;

    /** Prefer rollup tables when the requested range is at least this many days. */
    private int rollupPreferMinDays = 14;

    /** Optional ClickHouse / analytics warehouse dual-write + read path. */
    private ClickHouse clickhouse = new ClickHouse();

    public int getMaxQueryRangeDays() {
        return maxQueryRangeDays;
    }

    public void setMaxQueryRangeDays(int maxQueryRangeDays) {
        this.maxQueryRangeDays = maxQueryRangeDays;
    }

    public int getRollupPreferMinDays() {
        return rollupPreferMinDays;
    }

    public void setRollupPreferMinDays(int rollupPreferMinDays) {
        this.rollupPreferMinDays = rollupPreferMinDays;
    }

    public ClickHouse getClickhouse() {
        return clickhouse;
    }

    public void setClickhouse(ClickHouse clickhouse) {
        this.clickhouse = clickhouse;
    }

    public static class ClickHouse {
        /** Master switch for dual-write + schema init. */
        private boolean enabled = false;
        /** When enabled, prefer ClickHouse for cost aggregate reads (fallback to RDBMS on error). */
        private boolean readEnabled = true;
        private String url = "http://localhost:8123";
        private String username = "default";
        private String password = "";
        private String database = "agent_retry";
        private int flushIntervalMs = 1000;
        private int flushBatchSize = 500;
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 30000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isReadEnabled() {
            return readEnabled;
        }

        public void setReadEnabled(boolean readEnabled) {
            this.readEnabled = readEnabled;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public int getFlushIntervalMs() {
            return flushIntervalMs;
        }

        public void setFlushIntervalMs(int flushIntervalMs) {
            this.flushIntervalMs = flushIntervalMs;
        }

        public int getFlushBatchSize() {
            return flushBatchSize;
        }

        public void setFlushBatchSize(int flushBatchSize) {
            this.flushBatchSize = flushBatchSize;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }
    }
}
