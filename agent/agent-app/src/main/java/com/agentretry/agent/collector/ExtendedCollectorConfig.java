/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.collector;

import com.agentretry.agent.config.AgentProperties;
import com.agentretry.agent.detector.RetryDetector;
import com.agentretry.agent.git.ProjectContextEnricher;
import com.agentretry.agent.uploader.EventBuffer;
import com.agentretry.contract.SourceTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExtendedCollectorConfig {

    @Bean
    SessionCollector windsurfCollector(
            AgentProperties properties,
            EventBuffer eventBuffer,
            RetryDetector retryDetector,
            ObjectMapper objectMapper,
            EventPayloadBuilder payloadBuilder,
            ProjectContextEnricher projectContextEnricher
    ) {
        return new StandardJsonlCollector(
                properties, eventBuffer, retryDetector, objectMapper, payloadBuilder, projectContextEnricher,
                SourceTool.WINDSURF, "windsurf", "~/.codeium/windsurf/cascade"
        );
    }

    @Bean
    SessionCollector continueCollector(
            AgentProperties properties,
            EventBuffer eventBuffer,
            RetryDetector retryDetector,
            ObjectMapper objectMapper,
            EventPayloadBuilder payloadBuilder,
            ProjectContextEnricher projectContextEnricher
    ) {
        return new StandardJsonlCollector(
                properties, eventBuffer, retryDetector, objectMapper, payloadBuilder, projectContextEnricher,
                SourceTool.CONTINUE, "continue", "~/.continue/sessions"
        );
    }

    @Bean
    SessionCollector clineCollector(
            AgentProperties properties,
            EventBuffer eventBuffer,
            RetryDetector retryDetector,
            ObjectMapper objectMapper,
            EventPayloadBuilder payloadBuilder,
            ProjectContextEnricher projectContextEnricher
    ) {
        return new StandardJsonlCollector(
                properties, eventBuffer, retryDetector, objectMapper, payloadBuilder, projectContextEnricher,
                SourceTool.CLINE, "cline", "~/.cline/logs"
        );
    }

    @Bean
    SessionCollector aiderCollector(
            AgentProperties properties,
            EventBuffer eventBuffer,
            RetryDetector retryDetector,
            ObjectMapper objectMapper,
            EventPayloadBuilder payloadBuilder,
            ProjectContextEnricher projectContextEnricher
    ) {
        return new StandardJsonlCollector(
                properties, eventBuffer, retryDetector, objectMapper, payloadBuilder, projectContextEnricher,
                SourceTool.AIDER, "aider", "~/.aider"
        );
    }

    @Bean
    SessionCollector openCodeCollector(
            AgentProperties properties,
            EventBuffer eventBuffer,
            RetryDetector retryDetector,
            ObjectMapper objectMapper,
            EventPayloadBuilder payloadBuilder,
            ProjectContextEnricher projectContextEnricher
    ) {
        return new StandardJsonlCollector(
                properties, eventBuffer, retryDetector, objectMapper, payloadBuilder, projectContextEnricher,
                SourceTool.OPENCODE, "opencode", "~/.opencode/sessions"
        );
    }

    @Bean
    SessionCollector kiroIdeCollector(
            AgentProperties properties,
            EventBuffer eventBuffer,
            RetryDetector retryDetector,
            ObjectMapper objectMapper,
            EventPayloadBuilder payloadBuilder,
            ProjectContextEnricher projectContextEnricher
    ) {
        return new StandardJsonlCollector(
                properties, eventBuffer, retryDetector, objectMapper, payloadBuilder, projectContextEnricher,
                SourceTool.KIRO_IDE, "kiro_ide",
                "~/Library/Application Support/Kiro/User/globalStorage/kiro.kiroagent"
        );
    }
}
