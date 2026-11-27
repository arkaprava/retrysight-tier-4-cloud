/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import java.net.URI;
import java.util.Locale;

/**
 * Manager HTTP client. HTTPS uses the JVM default trust store; optional HTTP proxy via
 * {@code agent.manager.proxy-*} or standard {@code HTTP_PROXY}/{@code HTTPS_PROXY} env vars.
 */
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    @Bean
    public WebClient managerWebClient(AgentProperties properties) {
        HttpClient httpClient = HttpClient.create();

        ProxySettings proxy = resolveProxy(properties.getManager());
        if (proxy != null) {
            log.info("Configuring manager WebClient HTTP proxy {}:{}", proxy.host(), proxy.port());
            httpClient = httpClient.proxy(spec -> {
                ProxyProvider.Builder builder = spec
                        .type(ProxyProvider.Proxy.HTTP)
                        .host(proxy.host())
                        .port(proxy.port());
                if (StringUtils.hasText(proxy.username())) {
                    builder.username(proxy.username());
                    if (proxy.password() != null) {
                        builder.password(user -> proxy.password());
                    }
                }
            });
        }

        return WebClient.builder()
                .baseUrl(properties.getManager().getUrl())
                .defaultHeader("X-Agent-Api-Key", properties.getManager().getApiKey())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    static ProxySettings resolveProxy(AgentProperties.Manager manager) {
        if (StringUtils.hasText(manager.getProxyHost()) && manager.getProxyPort() != null) {
            return new ProxySettings(
                    manager.getProxyHost().trim(),
                    manager.getProxyPort(),
                    manager.getProxyUsername(),
                    manager.getProxyPassword()
            );
        }

        String managerUrl = manager.getUrl() == null ? "" : manager.getUrl().toLowerCase(Locale.ROOT);
        String env = managerUrl.startsWith("https:")
                ? firstNonBlank(System.getenv("HTTPS_PROXY"), System.getenv("https_proxy"),
                System.getenv("HTTP_PROXY"), System.getenv("http_proxy"))
                : firstNonBlank(System.getenv("HTTP_PROXY"), System.getenv("http_proxy"),
                System.getenv("HTTPS_PROXY"), System.getenv("https_proxy"));
        if (!StringUtils.hasText(env)) {
            return null;
        }
        try {
            URI uri = URI.create(env.contains("://") ? env : "http://" + env);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 80;
            if (!StringUtils.hasText(host)) {
                return null;
            }
            String user = null;
            String password = null;
            if (uri.getUserInfo() != null) {
                String[] parts = uri.getUserInfo().split(":", 2);
                user = parts[0];
                password = parts.length > 1 ? parts[1] : null;
            }
            return new ProxySettings(host, port, user, password);
        } catch (Exception ex) {
            log.warn("Ignoring invalid proxy URL from environment: {}", env);
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    record ProxySettings(String host, int port, String username, String password) {
    }
}
