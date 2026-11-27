/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.clickhouse;

import com.agentretry.manager.config.AnalyticsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Minimal ClickHouse HTTP interface client (no JDBC dependency).
 */
public class ClickHouseHttpClient {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseHttpClient.class);

    private final AnalyticsProperties.ClickHouse props;
    private final HttpClient httpClient;

    public ClickHouseHttpClient(AnalyticsProperties.ClickHouse props) {
        this.props = props;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(500, props.getConnectTimeoutMs())))
                .build();
    }

    public void execute(String sql) throws IOException, InterruptedException {
        post(sql, null, true);
    }

    /** Run DDL that must not require the target database to exist yet (e.g. CREATE DATABASE). */
    public void executeWithoutDatabase(String sql) throws IOException, InterruptedException {
        post(sql, null, false);
    }

    public void insertJsonEachRow(String table, String jsonLines) throws IOException, InterruptedException {
        String query = "INSERT INTO " + table + " FORMAT JSONEachRow";
        post(query, jsonLines, true);
    }

    public String queryJson(String sql) throws IOException, InterruptedException {
        String withFormat = sql.trim();
        if (!withFormat.toUpperCase().contains("FORMAT ")) {
            withFormat = withFormat + " FORMAT JSON";
        }
        return post(withFormat, null, true);
    }

    private String post(String query, String body, boolean includeDatabase) throws IOException, InterruptedException {
        String base = props.getUrl().replaceAll("/+$", "");
        String q = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String uriStr = includeDatabase
                ? base + "/?database=" + URLEncoder.encode(props.getDatabase(), StandardCharsets.UTF_8) + "&query=" + q
                : base + "/?query=" + q;
        URI uri = URI.create(uriStr);

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(Math.max(1000, props.getReadTimeoutMs())))
                .header("Content-Type", "text/plain; charset=UTF-8");

        if (props.getUsername() != null && !props.getUsername().isBlank()) {
            String token = props.getUsername() + ":" + (props.getPassword() == null ? "" : props.getPassword());
            builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8)));
        }

        HttpRequest request = builder
                .POST(body == null || body.isEmpty()
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 300) {
            String msg = "ClickHouse HTTP " + response.statusCode() + ": " + truncate(response.body(), 500);
            log.warn(msg);
            throw new IOException(msg);
        }
        return response.body() == null ? "" : response.body();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
