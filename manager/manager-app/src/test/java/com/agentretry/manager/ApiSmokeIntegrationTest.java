/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CI smoke: login → platformFeatures → dashboardSummary → costSummary.
 * Ingest API-key checks are covered by {@code scripts/validation/smoke.sh} (S12).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev-h2")
class ApiSmokeIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void loginDashboardAndCostSummarySmoke() throws Exception {
        String base = "http://localhost:" + port;

        ResponseEntity<String> login = restTemplate.postForEntity(
                base + "/api/v1/auth/login",
                Map.of("email", "secadmin@local", "password", "changeme"),
                String.class
        );
        assertEquals(HttpStatus.OK, login.getStatusCode());
        JsonNode loginJson = objectMapper.readTree(login.getBody());
        String token = loginJson.path("token").asText();
        assertFalse(token.isBlank(), "JWT token expected");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        JsonNode features = graphql(base, headers, "{ platformFeatures { installationTier costEnabled reportsEnabled mcpEnabled } }");
        assertFalse(features.has("errors"), () -> String.valueOf(features.get("errors")));
        assertNotNull(features.path("data").path("platformFeatures").path("installationTier").asText(null));

        JsonNode dashboard = graphql(base, headers, "{ dashboardSummary { totalTasks avgRetriesPerTask retryTokenCostUsd } }");
        assertFalse(dashboard.has("errors"), () -> String.valueOf(dashboard.get("errors")));
        assertTrue(dashboard.path("data").path("dashboardSummary").path("totalTasks").isNumber());

        Instant from = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant to = Instant.now();
        String costQuery = """
                query($from: DateTime, $to: DateTime) {
                  costSummary(from: $from, to: $to) {
                    totalCostUsd
                    retryCostUsd
                    costByTool { tool value }
                  }
                }
                """;
        JsonNode cost = graphql(base, headers, costQuery, Map.of("from", from.toString(), "to", to.toString()));
        assertFalse(cost.has("errors"), () -> "costSummary must not error (regression :toGROUP): " + cost.get("errors"));
        assertTrue(cost.path("data").path("costSummary").path("totalCostUsd").isNumber());
    }

    private JsonNode graphql(String base, HttpHeaders headers, String query) throws Exception {
        return graphql(base, headers, query, Map.of());
    }

    private JsonNode graphql(String base, HttpHeaders headers, String query, Map<String, ?> variables) throws Exception {
        Map<String, Object> body = Map.of("query", query, "variables", variables);
        ResponseEntity<String> response = restTemplate.exchange(
                base + "/graphql",
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return objectMapper.readTree(response.getBody());
    }
}
