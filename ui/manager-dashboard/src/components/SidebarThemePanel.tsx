/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { UI_THEMES, THEME_ICONS, useTheme } from '../theme/ThemeContext'

export default function SidebarThemePanel() {
  const { theme, setTheme, themeLabels } = useTheme()

  return (
    <div className="sidebar-theme-panel">
      <p className="sidebar-theme-intro">
        Rocket Monitor — glassmorphic cockpit with teal-sky live metrics and lavender hovers.
      </p>
      <div className="theme-options" role="radiogroup" aria-label="UI theme">
        {UI_THEMES.map((option) => (
          <button
            key={option}
            type="button"
            role="radio"
            aria-checked={theme === option}
            className={`theme-option${theme === option ? ' active' : ''}`}
            onClick={() => setTheme(option)}
          >
            <span className="theme-option-icon" aria-hidden="true">
              {THEME_ICONS[option]}
            </span>
            <span className="theme-option-copy">
              <span className="theme-option-label">{themeLabels[option]}</span>
              {option === 'monitor' && (
                <span className="theme-option-hint">Void-black glass cockpit (recommended)</span>
              )}
              {option === 'monitor-light' && (
                <span className="theme-option-hint">Frosted light panels for daytime</span>
              )}
              {option === 'auto' && (
                <span className="theme-option-hint">Monitor dark/light from system</span>
              )}
            </span>
          </button>
        ))}
      </div>
    </div>
  )
}
