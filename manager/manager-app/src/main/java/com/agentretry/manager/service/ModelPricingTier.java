/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

/**
 * Per-million-token rates in USD for a model family.
 *
 * @param inputPerMillion       non-cached input tokens
 * @param outputPerMillion      generated output tokens
 * @param cacheReadPerMillion   prompt cache read / cache hit tokens
 * @param cacheWritePerMillion  prompt cache write tokens
 * @param reasoningPerMillion   hidden reasoning tokens (billed like output when applicable)
 */
record ModelPricingTier(
        double inputPerMillion,
        double outputPerMillion,
        double cacheReadPerMillion,
        double cacheWritePerMillion,
        double reasoningPerMillion
) {
    static ModelPricingTier of(double input, double output) {
        return new ModelPricingTier(input, output, input * 0.1, input * 1.25, output);
    }

    static ModelPricingTier withCache(double input, double output, double cacheRead, double cacheWrite) {
        return new ModelPricingTier(input, output, cacheRead, cacheWrite, output);
    }
}
