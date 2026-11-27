/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.cli;

import com.agentretry.agent.discovery.CollectorDiscoveryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "agent.cli.command", havingValue = "discover")
public class DiscoverCliRunner implements ApplicationRunner {

    private final CollectorDiscoveryService discoveryService;
    private final ObjectMapper objectMapper;

    public DiscoverCliRunner(CollectorDiscoveryService discoveryService, ObjectMapper objectMapper) {
        this.discoveryService = discoveryService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<Map<String, Object>> rows = discoveryService.refresh().stream()
                .map(result -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("configType", result.configType());
                    row.put("status", result.status().name());
                    row.put("watchPath", result.watchPath() == null ? null : result.watchPath().toString());
                    row.put("detail", result.detail());
                    row.put("newestLogAt", result.newestLogAt());
                    return row;
                })
                .toList();
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rows));
        System.exit(0);
    }
}
