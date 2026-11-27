/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * Canonical theme configuration for the Vite HTML shell (FOUC bootstrap)
 * and React ThemeContext.
 */

export const UI_THEME_STORAGE_KEY = 'ui-theme'

export const UI_THEMES = [
  'monitor',
  'monitor-light',
  'high-contrast',
  'auto',
] as const

export type UiTheme = (typeof UI_THEMES)[number]

export type ResolvedUiTheme = Exclude<UiTheme, 'auto'>

export const DEFAULT_UI_THEME: UiTheme = 'auto'

export const THEME_LABELS: Record<UiTheme, string> = {
  monitor: 'Rocket Monitor',
  'monitor-light': 'Monitor Light',
  'high-contrast': 'High Contrast',
  auto: 'Auto',
}

export const THEME_ICONS: Record<UiTheme, string> = {
  monitor: '⬡',
  'monitor-light': '◫',
  'high-contrast': '◆',
  auto: '◐',
}

export const LEGACY_THEME_MAP: Record<string, UiTheme> = {
  light: 'monitor-light',
  dark: 'monitor',
  fortify: 'monitor',
  'fortify-dark': 'monitor',
  tailadmin: 'monitor',
  'tailadmin-dark': 'monitor',
}

export function resolveTheme(
  theme: UiTheme,
  prefersDark = typeof window !== 'undefined'
    && window.matchMedia('(prefers-color-scheme: dark)').matches,
): ResolvedUiTheme {
  if (theme === 'auto') {
    return prefersDark ? 'monitor' : 'monitor-light'
  }
  return theme
}

export function normalizeStoredTheme(value: string | null): UiTheme | null {
  if (value !== null && (UI_THEMES as readonly string[]).includes(value)) {
    return value as UiTheme
  }
  if (value && LEGACY_THEME_MAP[value]) {
    return LEGACY_THEME_MAP[value]
  }
  return null
}

export function readStoredTheme(): UiTheme {
  try {
    const stored = localStorage.getItem(UI_THEME_STORAGE_KEY)
    const normalized = normalizeStoredTheme(stored)
    if (normalized) {
      return normalized
    }
  } catch {
    // ignore storage errors
  }
  return DEFAULT_UI_THEME
}

export function applyTheme(theme: UiTheme, prefersDark?: boolean) {
  const resolved = resolveTheme(theme, prefersDark)
  document.documentElement.setAttribute('data-theme', resolved)
  document.documentElement.setAttribute('data-theme-preference', theme)
}
