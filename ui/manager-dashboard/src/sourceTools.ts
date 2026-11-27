/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

export {
  formatSourceTool,
  usePlatformFeatures,
  useSourceTools,
  type InstallationTier,
  type PlatformFeaturesData,
  type SourceToolInfo,
} from './platformFeatures'

export type SourceTool = string

export const SOURCE_TOOLS = [
  'CURSOR',
  'CLAUDE_CODE',
  'CLAUDE_COWORK',
  'CODEX',
  'KIRO',
  'WARP',
  'COPILOT',
  'WINDSURF',
  'CONTINUE',
  'CLINE',
  'AIDER',
  'OPENCODE',
  'KIRO_IDE',
] as const
