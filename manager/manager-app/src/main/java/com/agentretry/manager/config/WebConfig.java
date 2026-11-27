/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig {

    private static final String[] ALLOWED_HEADERS = {
            "Authorization",
            "Content-Type",
            "X-Agent-Api-Key",
            "X-Agent-Id"
    };

    @Bean
    public WebMvcConfigurer corsConfigurer(
            @Value("${spring.graphql.cors.allowed-origins:http://localhost:5173,http://localhost:3000}") String allowedOrigins
    ) {
        String[] origins = allowedOrigins.split(",");
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders(ALLOWED_HEADERS);
            }
        };
    }
}
