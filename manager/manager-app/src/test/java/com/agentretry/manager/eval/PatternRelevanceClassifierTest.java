/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternRelevanceClassifierTest {

    private final PatternRelevanceClassifier classifier = new PatternRelevanceClassifier();

    @Test
    void flagsNonEngineeringRecipeTopic() {
        var result = classifier.assess("write dinner recipe", List.of("help me plan meals for the week"));
        assertTrue(result.suspicious());
    }

    @Test
    void acceptsBugFixTopic() {
        var result = classifier.assess(
                "fix auth bug",
                List.of("debug login error in spring api endpoint")
        );
        assertFalse(result.suspicious());
    }

    @Test
    void flagsHomeworkWithoutCodeSignals() {
        var result = classifier.assess(
                "history homework essay",
                List.of("write an essay about world war 2 battles")
        );
        assertTrue(result.suspicious());
    }

    @Test
    void flagsChickenPastaTopic() {
        var result = classifier.assess(
                "how make chicken pasta",
                List.of("how to make chicken pasta for dinner tonight")
        );
        assertTrue(result.suspicious());
    }
}
