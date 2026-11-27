/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.manager.domain.ModelPricingEntity;
import com.agentretry.manager.repository.ModelPricingRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Estimates token usage cost from published provider pricing.
 *
 * <p>Sources (USD per 1M tokens, verified July 2026):
 * <ul>
 *   <li>Anthropic Claude API — {@code platform.claude.com/docs/en/about-claude/pricing}
 *       (Opus 4.x $5/$25, Sonnet 4.x $3/$15, Haiku 4.5 $1/$5; cache read/write multipliers)</li>
 *   <li>OpenAI API — {@code developers.openai.com/api/docs/pricing}
 *       (GPT-4o $2.50/$10, GPT-5 $1.25/$10, GPT-5.3 Codex $1.75/$14, o1 $15/$60)</li>
 *   <li>Cursor proxy models — {@code cursor.com/docs/models-and-pricing}
 *       (Auto $1.25/$6, Composer 2.5 $0.50/$2.50 standard or $3/$15 fast)</li>
 *   <li>Chinese LLM APIs (July 2026):
 *       DeepSeek V4 Flash/Pro, Alibaba Qwen Turbo/Plus/Max, Zhipu GLM Flash/Air/4.7/5,
 *       Moonshot Kimi, Baidu Ernie, ByteDance Doubao, MiniMax, Baichuan, 01.AI Yi, Tencent Hunyuan</li>
 * </ul>
 */
@Service
public class ModelPricingService {

    private static final double MILLION = 1_000_000.0;

    private static final ModelPricingTier DEFAULT = ModelPricingTier.of(3.0, 15.0);

    private static final Map<String, ModelPricingTier> PRICING = Map.ofEntries(
            Map.entry("claude-opus", ModelPricingTier.withCache(5.0, 25.0, 0.50, 6.25)),
            Map.entry("claude-sonnet", ModelPricingTier.withCache(3.0, 15.0, 0.30, 3.75)),
            Map.entry("claude-haiku", ModelPricingTier.withCache(1.0, 5.0, 0.10, 1.25)),
            Map.entry("claude-opus-legacy", ModelPricingTier.withCache(15.0, 75.0, 1.50, 18.75)),
            Map.entry("gpt-4o", ModelPricingTier.withCache(2.50, 10.0, 1.25, 2.50)),
            Map.entry("gpt-4o-mini", ModelPricingTier.withCache(0.15, 0.60, 0.075, 0.15)),
            Map.entry("gpt-4.1", ModelPricingTier.withCache(2.0, 8.0, 0.50, 2.0)),
            Map.entry("gpt-4.1-mini", ModelPricingTier.withCache(0.40, 1.60, 0.10, 0.40)),
            Map.entry("gpt-5", ModelPricingTier.of(1.25, 10.0)),
            Map.entry("gpt-5.3-codex", ModelPricingTier.of(1.75, 14.0)),
            Map.entry("o1", ModelPricingTier.withCache(15.0, 60.0, 7.50, 15.0)),
            Map.entry("o3-mini", ModelPricingTier.withCache(1.10, 4.40, 0.55, 1.10)),
            Map.entry("o3", ModelPricingTier.of(2.0, 8.0)),
            Map.entry("cursor-auto", ModelPricingTier.withCache(1.25, 6.0, 0.25, 1.25)),
            Map.entry("composer-2.5", ModelPricingTier.withCache(0.50, 2.50, 0.125, 0.50)),
            Map.entry("composer-2.5-fast", ModelPricingTier.withCache(3.0, 15.0, 0.30, 3.0)),
            Map.entry("composer-1", ModelPricingTier.withCache(1.25, 10.0, 0.125, 1.25)),
            // DeepSeek — api-docs.deepseek.com
            Map.entry("deepseek-v4-flash", ModelPricingTier.withCache(0.14, 0.28, 0.0028, 0.14)),
            Map.entry("deepseek-v4-pro", ModelPricingTier.withCache(0.435, 0.87, 0.003625, 0.435)),
            // Qwen — Alibaba Cloud Model Studio
            Map.entry("qwen-turbo", ModelPricingTier.of(0.05, 0.20)),
            Map.entry("qwen-plus", ModelPricingTier.of(0.40, 1.20)),
            Map.entry("qwen-max", ModelPricingTier.withCache(1.25, 3.75, 0.295, 1.25)),
            // GLM — docs.z.ai
            Map.entry("glm-flash", ModelPricingTier.of(0.0, 0.0)),
            Map.entry("glm-air", ModelPricingTier.withCache(0.20, 1.10, 0.03, 0.20)),
            Map.entry("glm-4.7", ModelPricingTier.withCache(0.60, 2.20, 0.11, 0.60)),
            Map.entry("glm-5", ModelPricingTier.withCache(1.40, 4.40, 0.26, 1.40)),
            // Moonshot Kimi — platform.moonshot.cn
            Map.entry("moonshot-v1", ModelPricingTier.withCache(0.96, 3.98, 0.16, 0.96)),
            // Baidu Ernie — cloud.baidu.com
            Map.entry("ernie-4", ModelPricingTier.of(0.59, 2.65)),
            // ByteDance Doubao — volcengine.com
            Map.entry("doubao-pro", ModelPricingTier.of(0.12, 0.29)),
            Map.entry("doubao-seed", ModelPricingTier.of(0.88, 4.42)),
            // MiniMax — api.minimax.chat
            Map.entry("minimax-m2", ModelPricingTier.of(0.30, 1.20)),
            // Baichuan — platform.baichuan-ai.com
            Map.entry("baichuan", ModelPricingTier.of(0.30, 0.60)),
            // 01.AI Yi — platform.lingyiwanwu.com
            Map.entry("yi-large", ModelPricingTier.of(0.40, 1.20)),
            // Tencent Hunyuan — cloud.tencent.com
            Map.entry("hunyuan", ModelPricingTier.of(0.45, 1.80))
    );

    private final ModelPricingRepository modelPricingRepository;
    private volatile Map<String, ModelPricingTier> dbOverrides = Map.of();

    public ModelPricingService(ModelPricingRepository modelPricingRepository) {
        this.modelPricingRepository = modelPricingRepository;
    }

    @PostConstruct
    void loadDbOverrides() {
        refreshDbOverrides();
    }

    public void refreshDbOverrides() {
        Map<String, ModelPricingTier> loaded = new HashMap<>();
        for (ModelPricingEntity entity : modelPricingRepository.findAll()) {
            loaded.put(entity.getModelKey(), toTier(entity));
        }
        dbOverrides = Map.copyOf(loaded);
    }

    /**
     * Maps raw model strings from agents (e.g. "cursor", "claude-sonnet-4", "gpt-4o") to a pricing tier key.
     */
    public String normalizeModel(String model) {
        if (model == null || model.isBlank()) {
            return "default";
        }

        String normalized = model.trim().toLowerCase(Locale.ROOT);

        if (normalized.equals("cursor") || normalized.equals("auto")) {
            return "cursor-auto";
        }
        if (normalized.equals("codex")) {
            return "gpt-5.3-codex";
        }

        if (normalized.contains("composer")) {
            if (normalized.contains("fast")) {
                return "composer-2.5-fast";
            }
            return "composer-2.5";
        }

        if (normalized.contains("opus")) {
            if (normalized.contains("4.1") || normalized.contains("4-1") || normalized.contains("3")) {
                return "claude-opus-legacy";
            }
            return "claude-opus";
        }
        if (normalized.contains("haiku")) {
            return "claude-haiku";
        }
        if (normalized.contains("sonnet") || normalized.contains("claude")) {
            return "claude-sonnet";
        }

        if (normalized.contains("o1-pro")) {
            return "o1";
        }
        if (normalized.contains("o1")) {
            return "o1";
        }
        if (normalized.contains("o3-mini")) {
            return "o3-mini";
        }
        if (normalized.contains("o3")) {
            return "o3";
        }
        if (normalized.contains("codex") || normalized.contains("gpt-5.3") || normalized.contains("gpt5.3")) {
            return "gpt-5.3-codex";
        }
        if (normalized.contains("gpt-5") || normalized.contains("gpt5")) {
            return "gpt-5";
        }
        if (normalized.contains("4o-mini") || normalized.contains("gpt-4o-mini")) {
            return "gpt-4o-mini";
        }
        if (normalized.contains("4.1-mini") || normalized.contains("gpt-4.1-mini")) {
            return "gpt-4.1-mini";
        }
        if (normalized.contains("4.1") || normalized.contains("gpt-4.1")) {
            return "gpt-4.1";
        }
        if (normalized.contains("4o") || normalized.contains("gpt-4o")) {
            return "gpt-4o";
        }
        if (normalized.contains("gpt")) {
            return "gpt-4o";
        }

        if (normalized.contains("deepseek")) {
            if (normalized.contains("pro") || normalized.contains("v4-pro")) {
                return "deepseek-v4-pro";
            }
            return "deepseek-v4-flash";
        }

        if (normalized.contains("qwen")) {
            if (normalized.contains("turbo") || normalized.contains("flash")) {
                return "qwen-turbo";
            }
            if (normalized.contains("max") || normalized.contains("3.7")) {
                return "qwen-max";
            }
            if (normalized.contains("plus")) {
                return "qwen-plus";
            }
            return "qwen-plus";
        }

        if (normalized.contains("glm") || normalized.contains("chatglm")) {
            if (normalized.contains("flash")) {
                return "glm-flash";
            }
            if (normalized.contains("air")) {
                return "glm-air";
            }
            if (normalized.contains("glm-5") || normalized.contains("glm5")) {
                return "glm-5";
            }
            return "glm-4.7";
        }

        if (normalized.contains("moonshot") || normalized.contains("kimi")) {
            return "moonshot-v1";
        }

        if (normalized.contains("ernie") || normalized.contains("文心")) {
            return "ernie-4";
        }

        if (normalized.contains("doubao") || normalized.contains("seed-2") || normalized.contains("seed2")) {
            if (normalized.contains("seed") || normalized.contains("2.1")) {
                return "doubao-seed";
            }
            return "doubao-pro";
        }

        if (normalized.contains("minimax") || normalized.contains("abab")) {
            return "minimax-m2";
        }

        if (normalized.contains("baichuan")) {
            return "baichuan";
        }

        if (normalized.contains("yi-large") || normalized.contains("yi-medium") || normalized.contains("yi-light")
                || normalized.contains("yi-34b") || normalized.contains("yi-")) {
            return "yi-large";
        }

        if (normalized.contains("hunyuan")) {
            return "hunyuan";
        }

        return "default";
    }

    public ModelPricingTier tierForModel(String model) {
        String key = normalizeModel(model);
        ModelPricingTier dbTier = dbOverrides.get(key);
        if (dbTier != null) {
            return dbTier;
        }
        return PRICING.getOrDefault(key, dbOverrides.getOrDefault("default", DEFAULT));
    }

    public BigDecimal estimateCost(
            String model,
            long inputTokens,
            long outputTokens,
            long cacheReadTokens,
            long cacheWriteTokens,
            long reasoningTokens
    ) {
        ModelPricingTier tier = tierForModel(model);

        double costUsd = (
                inputTokens * tier.inputPerMillion()
                        + outputTokens * tier.outputPerMillion()
                        + cacheReadTokens * tier.cacheReadPerMillion()
                        + cacheWriteTokens * tier.cacheWritePerMillion()
                        + reasoningTokens * tier.reasoningPerMillion()
        ) / MILLION;

        return BigDecimal.valueOf(costUsd).setScale(6, RoundingMode.HALF_UP);
    }

    public BigDecimal estimateCost(TokenUsageCounts counts) {
        return estimateCost(
                counts.model(),
                counts.inputTokens(),
                counts.outputTokens(),
                counts.cacheReadTokens(),
                counts.cacheWriteTokens(),
                counts.reasoningTokens()
        );
    }

    private static ModelPricingTier toTier(ModelPricingEntity entity) {
        return new ModelPricingTier(
                entity.getInputPerMillion().doubleValue(),
                entity.getOutputPerMillion().doubleValue(),
                entity.getCacheReadPerMillion().doubleValue(),
                entity.getCacheWritePerMillion().doubleValue(),
                entity.getReasoningPerMillion().doubleValue()
        );
    }

    public record TokenUsageCounts(
            String model,
            long inputTokens,
            long outputTokens,
            long cacheReadTokens,
            long cacheWriteTokens,
            long reasoningTokens
    ) {
    }
}
