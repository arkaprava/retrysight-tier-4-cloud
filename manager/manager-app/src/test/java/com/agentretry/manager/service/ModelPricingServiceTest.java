/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.manager.repository.ModelPricingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelPricingServiceTest {

    @Mock
    private ModelPricingRepository modelPricingRepository;

    private ModelPricingService pricingService;

    @BeforeEach
    void setUp() {
        when(modelPricingRepository.findAll()).thenReturn(List.of());
        pricingService = new ModelPricingService(modelPricingRepository);
        pricingService.refreshDbOverrides();
    }

    @Test
    void normalizesCommonModelAliases() {
        assertEquals("cursor-auto", pricingService.normalizeModel("cursor"));
        assertEquals("gpt-5.3-codex", pricingService.normalizeModel("codex"));
        assertEquals("claude-sonnet", pricingService.normalizeModel("claude-sonnet-4"));
        assertEquals("claude-opus", pricingService.normalizeModel("claude-opus-4-8"));
        assertEquals("gpt-4o", pricingService.normalizeModel("gpt-4o-2024-08-06"));
        assertEquals("composer-2.5", pricingService.normalizeModel("composer-2.5"));
    }

    @Test
    void normalizesChineseModelAliases() {
        assertEquals("deepseek-v4-flash", pricingService.normalizeModel("deepseek-chat"));
        assertEquals("deepseek-v4-flash", pricingService.normalizeModel("deepseek-reasoner"));
        assertEquals("deepseek-v4-pro", pricingService.normalizeModel("deepseek-v4-pro"));
        assertEquals("qwen-turbo", pricingService.normalizeModel("qwen-turbo"));
        assertEquals("qwen-plus", pricingService.normalizeModel("qwen-plus-latest"));
        assertEquals("qwen-max", pricingService.normalizeModel("qwen3.7-max"));
        assertEquals("glm-flash", pricingService.normalizeModel("glm-4.7-flash"));
        assertEquals("glm-4.7", pricingService.normalizeModel("glm-4.7"));
        assertEquals("glm-5", pricingService.normalizeModel("glm-5.2"));
        assertEquals("moonshot-v1", pricingService.normalizeModel("moonshot-v1-128k"));
        assertEquals("moonshot-v1", pricingService.normalizeModel("kimi-k2.6"));
        assertEquals("ernie-4", pricingService.normalizeModel("ernie-5.1"));
        assertEquals("doubao-pro", pricingService.normalizeModel("doubao-1.5-pro"));
        assertEquals("doubao-seed", pricingService.normalizeModel("doubao-seed-2.1-pro"));
        assertEquals("minimax-m2", pricingService.normalizeModel("minimax-m2.5"));
        assertEquals("baichuan", pricingService.normalizeModel("baichuan4-turbo"));
        assertEquals("yi-large", pricingService.normalizeModel("yi-large"));
        assertEquals("hunyuan", pricingService.normalizeModel("hunyuan-turbo"));
    }

    @Test
    void estimatesDeepSeekFlashTurnCost() {
        // 10k input + 2k output at V4 Flash ($0.14 / $0.28 per MTok) => $0.00196
        BigDecimal cost = pricingService.estimateCost("deepseek-v4-flash", 10_000, 2_000, 0, 0, 0);
        assertEquals(new BigDecimal("0.001960"), cost);
    }

    @Test
    void estimatesQwenMaxTurnCost() {
        // 10k input + 2k output at Qwen Max ($1.25 / $3.75 per MTok) => $0.020
        BigDecimal cost = pricingService.estimateCost("qwen3.7-max", 10_000, 2_000, 0, 0, 0);
        assertEquals(new BigDecimal("0.020000"), cost);
    }

    @Test
    void estimatesGlmFlashIsFree() {
        BigDecimal cost = pricingService.estimateCost("glm-4.7-flash", 100_000, 50_000, 0, 0, 0);
        assertEquals(new BigDecimal("0.000000"), cost);
    }

    @Test
    void estimatesClaudeSonnetTurnCost() {
        // 10k input + 2k output at Sonnet 4.x ($3 / $15 per MTok) => $0.06
        BigDecimal cost = pricingService.estimateCost("claude-sonnet-4.6", 10_000, 2_000, 0, 0, 0);
        assertEquals(new BigDecimal("0.060000"), cost);
    }

    @Test
    void estimatesCursorAutoWithCache() {
        // 5k input + 1k output + 20k cache read at Auto ($1.25 / $6 / $0.25)
        // => (6250 + 6000 + 5000) / 1M = $0.01725
        BigDecimal cost = pricingService.estimateCost("cursor", 5_000, 1_000, 20_000, 0, 0);
        assertEquals(new BigDecimal("0.017250"), cost);
    }

    @Test
    void estimatesCodexWithReasoningTokens() {
        // 8k input + 3k output + 1k reasoning at GPT-5.3 Codex ($1.75 / $14)
        // reasoning billed at output rate => (14000 + 42000 + 14000) / 1M = $0.07
        BigDecimal cost = pricingService.estimateCost("codex", 8_000, 3_000, 0, 0, 1_000);
        assertEquals(new BigDecimal("0.070000"), cost);
    }

    @Test
    void fallsBackForUnknownModels() {
        BigDecimal cost = pricingService.estimateCost("some-unknown-model-v9", 1_000_000, 0, 0, 0, 0);
        assertEquals(new BigDecimal("3.000000"), cost);
    }
}
