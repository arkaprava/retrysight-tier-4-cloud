/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.contract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceToolCatalogTest {

    @Test
    void coreToolsAvailableAtTierOne() {
        assertEquals(3, SourceToolCatalog.forTier(InstallationTier.TIER_1_CORE).size());
        assertTrue(SourceToolCatalog.forTier(InstallationTier.TIER_1_CORE).stream()
                .anyMatch(tool -> tool.id() == SourceTool.CURSOR));
    }

    @Test
    void tierTwoAddsExtendedCollectors() {
        assertTrue(SourceToolCatalog.forTier(InstallationTier.TIER_2_ANALYTICS).size()
                > SourceToolCatalog.forTier(InstallationTier.TIER_1_CORE).size());
    }

    @Test
    void findByConfigTypeResolvesAliases() {
        assertTrue(SourceToolDescriptor.findByConfigType("kiro_ide").isPresent());
        assertFalse(SourceToolDescriptor.findByConfigType("unknown").isPresent());
    }
}
