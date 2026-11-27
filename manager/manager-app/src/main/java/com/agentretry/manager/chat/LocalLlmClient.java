/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.chat;

import com.agentretry.manager.config.ChatProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class LocalLlmClient {
    private static final Logger log = LoggerFactory.getLogger(LocalLlmClient.class);

    private final ChatProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public LocalLlmClient(ChatProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
    }

    public boolean isReachable() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(properties.getOllamaBaseUrl()) + "/api/tags"))
                    .timeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() >= 200 && res.statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }

    public List<String> listModels() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(properties.getOllamaBaseUrl()) + "/api/tags"))
                    .timeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 300) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(res.body());
            List<String> models = new ArrayList<>();
            for (JsonNode m : root.path("models")) {
                String name = m.path("name").asText(null);
                if (name != null && !name.isBlank()) {
                    models.add(name);
                }
            }
            return models;
        } catch (Exception e) {
            return List.of();
        }
    }

    public LlmResponse chat(String model, List<Map<String, Object>> messages, List<ChatDtos.ToolSpec> tools) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model != null && !model.isBlank() ? model : properties.getModel());
            body.put("stream", false);
            body.put("temperature", properties.getTemperature());
            ArrayNode msgArr = body.putArray("messages");
            for (Map<String, Object> m : messages) {
                msgArr.add(objectMapper.valueToTree(m));
            }
            if (tools != null && !tools.isEmpty()) {
                ArrayNode toolArr = body.putArray("tools");
                for (ChatDtos.ToolSpec t : tools) {
                    ObjectNode tool = toolArr.addObject();
                    tool.put("type", "function");
                    ObjectNode fn = tool.putObject("function");
                    fn.put("name", t.name());
                    fn.put("description", t.description());
                    fn.set("parameters", objectMapper.valueToTree(t.parameters()));
                }
            }

            String url = trimSlash(properties.getOllamaBaseUrl()) + "/v1/chat/completions";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 300) {
                throw new IllegalStateException("Ollama HTTP " + res.statusCode() + ": " + ChatPrivacy.truncate(res.body(), 400));
            }
            return parseOpenAiResponse(res.body());
        } catch (Exception e) {
            log.warn("Local LLM call failed: {}", e.getMessage());
            throw new IllegalStateException("Local LLM unavailable: " + e.getMessage(), e);
        }
    }

    private LlmResponse parseOpenAiResponse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode message = root.path("choices").path(0).path("message");
        String content = message.path("content").asText("");
        List<ToolCall> toolCalls = new ArrayList<>();
        for (JsonNode tc : message.path("tool_calls")) {
            String id = tc.path("id").asText("call_" + toolCalls.size());
            String name = tc.path("function").path("name").asText("");
            String args = tc.path("function").path("arguments").asText("{}");
            toolCalls.add(new ToolCall(id, name, args));
        }
        // Ollama native tool format sometimes nests differently
        if (toolCalls.isEmpty() && message.has("tool_calls")) {
            // already handled
        }
        return new LlmResponse(content == null ? "" : content, toolCalls, message);
    }

    private static String trimSlash(String url) {
        if (url == null) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public record ToolCall(String id, String name, String argumentsJson) {}
    public record LlmResponse(String content, List<ToolCall> toolCalls, JsonNode rawMessage) {
        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }
}
