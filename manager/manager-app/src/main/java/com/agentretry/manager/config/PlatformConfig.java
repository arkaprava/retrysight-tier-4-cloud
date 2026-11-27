/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        PlatformProperties.class,
        RetentionProperties.class,
        AnalyticsProperties.class,
        OtelProperties.class,
        AlertProperties.class,
        ChatProperties.class,
        AgentMonitorProperties.class,
        EvalProperties.class,
        ManagerSecurityProperties.class,
        PatternsProperties.class
})
public class PlatformConfig {
}
