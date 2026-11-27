/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentProxyResolveTest {

    @Test
    void usesExplicitProxyProperties() {
        AgentProperties.Manager manager = new AgentProperties.Manager();
        manager.setUrl("https://manager.example");
        manager.setProxyHost("proxy.corp");
        manager.setProxyPort(3128);

        AgentConfig.ProxySettings proxy = AgentConfig.resolveProxy(manager);
        assertNotNull(proxy);
        assertEquals("proxy.corp", proxy.host());
        assertEquals(3128, proxy.port());
    }

    @Test
    void returnsNullWithoutProxyConfig() {
        Assumptions.assumeTrue(
                System.getenv("HTTP_PROXY") == null
                        && System.getenv("HTTPS_PROXY") == null
                        && System.getenv("http_proxy") == null
                        && System.getenv("https_proxy") == null,
                "Skipping — proxy environment variables are set"
        );
        AgentProperties.Manager manager = new AgentProperties.Manager();
        manager.setUrl("http://localhost:28477");
        assertNull(AgentConfig.resolveProxy(manager));
    }
}
