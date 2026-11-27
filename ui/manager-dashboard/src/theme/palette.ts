/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import type { ResolvedUiTheme } from './theme.config'

export type ChartColors = {
  grid: string
  axis: string
  primary: string
  secondary: string
  success: string
  warning: string
  danger: string
  bar: string
  series: readonly [string, string, string, string, string, string]
}

export const CHART_SERIES = {
  monitor: ['#5bcff0', '#a78bfa', '#34d399', '#fbbf24', '#f472b6', '#38bdf8'] as const,
  'monitor-light': ['#0e9eb0', '#7c3aed', '#059669', '#d97706', '#db2777', '#0284c7'] as const,
  'high-contrast': ['#5bcff0', '#a78bfa', '#4ade80', '#facc15', '#fb923c', '#f472b6'] as const,
}

export const chartColorThemes: Record<ResolvedUiTheme, ChartColors> = {
  monitor: {
    grid: 'rgba(91, 207, 240, 0.08)',
    axis: '#64748b',
    primary: '#5bcff0',
    secondary: '#a78bfa',
    success: '#34d399',
    warning: '#fbbf24',
    danger: '#f87171',
    bar: '#5bcff0',
    series: CHART_SERIES.monitor,
  },
  'monitor-light': {
    grid: 'rgba(14, 158, 176, 0.1)',
    axis: '#64748b',
    primary: '#0e9eb0',
    secondary: '#7c3aed',
    success: '#059669',
    warning: '#d97706',
    danger: '#dc2626',
    bar: '#0e9eb0',
    series: CHART_SERIES['monitor-light'],
  },
  'high-contrast': {
    grid: 'rgba(255, 255, 255, 0.35)',
    axis: '#ffffff',
    primary: '#5bcff0',
    secondary: '#a78bfa',
    success: '#4ade80',
    warning: '#facc15',
    danger: '#f87171',
    bar: '#5bcff0',
    series: CHART_SERIES['high-contrast'],
  },
}

export const chartTooltipThemes: Record<ResolvedUiTheme, {
  backgroundColor: string
  border: string
  color: string
  boxShadow: string
}> = {
  monitor: {
    backgroundColor: 'rgba(11, 15, 25, 0.92)',
    border: '1px solid rgba(255, 255, 255, 0.06)',
    color: '#e8ecf4',
    boxShadow: '0 8px 32px rgba(0, 0, 0, 0.5)',
  },
  'monitor-light': {
    backgroundColor: 'rgba(255, 255, 255, 0.95)',
    border: '1px solid rgba(11, 15, 25, 0.08)',
    color: '#0f172a',
    boxShadow: '0 4px 16px rgba(11, 15, 25, 0.08)',
  },
  'high-contrast': {
    backgroundColor: '#000000',
    border: '2px solid #c4b5fd',
    color: '#ffffff',
    boxShadow: 'none',
  },
}
