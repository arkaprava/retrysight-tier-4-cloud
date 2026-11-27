/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

/** Tremor chart color tokens aligned with Rocket Monitor palette series. */
import { CHART_SERIES } from '../palette'

export const MONITOR_TREMOR_COLORS = [
  'indigo',
  'cyan',
  'emerald',
  'amber',
  'orange',
  'violet',
  'rose',
  'teal',
  'sky',
  'lime',
  'fuchsia',
  'blue',
] as const

export type MonitorTremorColor = (typeof MONITOR_TREMOR_COLORS)[number]

/** Hex series from Rocket Monitor theme — useful for custom overlays outside Tremor. */
export const monitorSeriesHex = CHART_SERIES.monitor

/** Stable per-tool colors so IDE charts stay consistent across pages. */
const SOURCE_TOOL_COLORS: Record<string, MonitorTremorColor> = {
  CURSOR: 'violet',
  CLAUDE_CODE: 'amber',
  CLAUDE_COWORK: 'orange',
  CODEX: 'emerald',
  KIRO: 'cyan',
  KIRO_IDE: 'sky',
  WARP: 'violet',
  COPILOT: 'blue',
  WINDSURF: 'teal',
  CONTINUE: 'orange',
  CLINE: 'rose',
  AIDER: 'lime',
  OPENCODE: 'fuchsia',
}

/**
 * Stable per-model family colors so LLM charts stay consistent across pages.
 * Matching is case-insensitive substring / prefix against the normalized model id.
 */
const MODEL_COLOR_RULES: Array<{ test: (model: string) => boolean; color: MonitorTremorColor }> = [
  { test: (m) => m === 'other' || m === 'unknown' || m === '', color: 'rose' },
  { test: (m) => m.includes('opus'), color: 'orange' },
  { test: (m) => m.includes('sonnet'), color: 'amber' },
  { test: (m) => m.includes('haiku'), color: 'lime' },
  { test: (m) => m.includes('claude'), color: 'amber' },
  { test: (m) => /\bo3\b/.test(m) || m.includes('o3-'), color: 'violet' },
  { test: (m) => /\bo1\b/.test(m) || m.includes('o1-'), color: 'fuchsia' },
  { test: (m) => m.includes('gpt-4o') || m.includes('gpt4o') || m.includes('4o-mini') || m.includes('4o-'), color: 'emerald' },
  { test: (m) => m.includes('gpt-4') || m.includes('gpt4'), color: 'cyan' },
  { test: (m) => m.includes('gpt-3') || m.includes('gpt3') || m.includes('gpt-35'), color: 'sky' },
  { test: (m) => m.includes('gpt'), color: 'cyan' },
  { test: (m) => m.includes('gemini'), color: 'teal' },
  { test: (m) => m.includes('llama'), color: 'blue' },
  { test: (m) => m.includes('mistral') || m.includes('mixtral'), color: 'fuchsia' },
  { test: (m) => m.includes('deepseek'), color: 'indigo' },
  { test: (m) => m.includes('qwen'), color: 'sky' },
  { test: (m) => m.includes('glm') || m.includes('chatglm'), color: 'teal' },
  { test: (m) => m.includes('kimi') || m.includes('moonshot'), color: 'violet' },
  { test: (m) => m.includes('ernie') || m.includes('文心'), color: 'blue' },
  { test: (m) => m.includes('doubao') || m.includes('seed-2'), color: 'orange' },
  { test: (m) => m.includes('minimax') || m.includes('abab'), color: 'rose' },
  { test: (m) => m.includes('baichuan'), color: 'lime' },
  { test: (m) => /\byi[-_]/.test(m), color: 'cyan' },
  { test: (m) => m.includes('hunyuan'), color: 'amber' },
  { test: (m) => m.includes('grok'), color: 'violet' },
  { test: (m) => m.includes('codex'), color: 'emerald' },
]

function hashString(value: string): number {
  let hash = 0
  for (let i = 0; i < value.length; i += 1) {
    hash = (hash * 31 + value.charCodeAt(i)) >>> 0
  }
  return hash
}

function normalizeToolKey(tool: string): string {
  return tool
    .trim()
    .toUpperCase()
    .replace(/[\s-]+/g, '_')
}

function normalizeModelKey(model: string): string {
  return model.trim().toLowerCase()
}

function colorFromHash(key: string): MonitorTremorColor {
  return MONITOR_TREMOR_COLORS[hashString(key) % MONITOR_TREMOR_COLORS.length]
}

export function tremorColors(count = 2): MonitorTremorColor[] {
  return MONITOR_TREMOR_COLORS.slice(0, Math.max(1, count)) as MonitorTremorColor[]
}

/** Stable Tremor color for a source IDE / collector tool. */
export function colorForSourceTool(tool: string): MonitorTremorColor {
  const key = normalizeToolKey(tool)
  const mapped = SOURCE_TOOL_COLORS[key]
  if (mapped) {
    return mapped
  }
  return colorFromHash(key)
}

/** Stable Tremor color for an LLM model id / display name. */
export function colorForModel(model: string): MonitorTremorColor {
  const key = normalizeModelKey(model)
  for (const rule of MODEL_COLOR_RULES) {
    if (rule.test(key)) {
      return rule.color
    }
  }
  return colorFromHash(key)
}
