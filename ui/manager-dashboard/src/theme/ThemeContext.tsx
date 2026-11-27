/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { createContext, useContext, useEffect, useMemo, useState, ReactNode } from 'react'
import {
  applyTheme,
  readStoredTheme,
  resolveTheme,
  THEME_ICONS,
  THEME_LABELS,
  UI_THEME_STORAGE_KEY,
  UI_THEMES,
  type ResolvedUiTheme,
  type UiTheme,
} from './theme.config'

export {
  applyTheme,
  resolveTheme,
  THEME_ICONS,
  UI_THEME_STORAGE_KEY,
  UI_THEMES,
  type ResolvedUiTheme,
  type UiTheme,
}

type ThemeContextValue = {
  theme: UiTheme
  resolvedTheme: ResolvedUiTheme
  setTheme: (theme: UiTheme) => void
  themeLabels: typeof THEME_LABELS
}

const ThemeContext = createContext<ThemeContextValue | null>(null)

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<UiTheme>(() => readStoredTheme())
  const [systemDark, setSystemDark] = useState(() =>
    typeof window !== 'undefined' && window.matchMedia('(prefers-color-scheme: dark)').matches,
  )
  const resolvedTheme = useMemo(
    () => resolveTheme(theme, systemDark),
    [theme, systemDark],
  )

  useEffect(() => {
    applyTheme(theme, systemDark)
    localStorage.setItem(UI_THEME_STORAGE_KEY, theme)
  }, [theme, systemDark])

  useEffect(() => {
    if (theme !== 'auto') {
      return undefined
    }

    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const handleChange = (event: MediaQueryListEvent) => {
      setSystemDark(event.matches)
    }
    media.addEventListener('change', handleChange)
    return () => media.removeEventListener('change', handleChange)
  }, [theme])

  const value = useMemo(
    () => ({
      theme,
      resolvedTheme,
      setTheme: setThemeState,
      themeLabels: THEME_LABELS,
    }),
    [theme, resolvedTheme],
  )

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme() {
  const ctx = useContext(ThemeContext)
  if (!ctx) {
    throw new Error('useTheme must be used within ThemeProvider')
  }
  return ctx
}
