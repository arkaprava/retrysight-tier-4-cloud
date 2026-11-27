/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.contract;

import java.util.List;

public final class SourceToolCatalog {

    private SourceToolCatalog() {
    }

    public static List<SourceToolDescriptor> all() {
        return List.of(
                core(SourceTool.CURSOR, "Cursor", "cursor", "~/.cursor/projects",
                        SourceToolDescriptor.LogFormat.JSONL, caps(true, true, true, true, true, true)),
                core(SourceTool.CLAUDE_CODE, "Claude Code", "claude_code", "~/.claude/projects",
                        SourceToolDescriptor.LogFormat.JSONL, caps(true, true, true, true, true, true)),
                // Org/desktop agentic work via OpenTelemetry (not local JSONL).
                tier3(SourceTool.CLAUDE_COWORK, "Claude Cowork", "claude_cowork", "otel://cowork",
                        SourceToolDescriptor.LogFormat.OTEL, caps(true, true, true, true, true, false)),
                core(SourceTool.CODEX, "Codex", "codex", "~/.codex/sessions",
                        SourceToolDescriptor.LogFormat.EVENTS_JSONL, caps(true, true, true, true, false, true)),
                tier2(SourceTool.KIRO, "Kiro CLI", "kiro", "~/.kiro/sessions/cli",
                        SourceToolDescriptor.LogFormat.JSONL, caps(true, true, true, true, true, true)),
                tier2(SourceTool.KIRO_IDE, "Kiro IDE", "kiro_ide",
                        "~/Library/Application Support/Kiro/User/globalStorage/kiro.kiroagent",
                        SourceToolDescriptor.LogFormat.JSON_DOC, caps(true, false, true, true, false, true)),
                tier2(SourceTool.WARP, "Warp", "warp", "~/.warp/warp.sqlite",
                        SourceToolDescriptor.LogFormat.SQLITE, caps(true, true, false, true, false, false)),
                tier2(SourceTool.COPILOT, "Copilot", "copilot", "~/.copilot/session-state",
                        SourceToolDescriptor.LogFormat.EVENTS_JSONL, caps(true, true, true, true, true, true)),
                tier2(SourceTool.WINDSURF, "Windsurf", "windsurf", "~/.codeium/windsurf/cascade",
                        SourceToolDescriptor.LogFormat.JSONL, caps(true, true, true, true, true, false)),
                tier2(SourceTool.CONTINUE, "Continue", "continue", "~/.continue/sessions",
                        SourceToolDescriptor.LogFormat.JSONL, caps(true, true, true, true, false, false)),
                tier2(SourceTool.CLINE, "Cline", "cline", "~/.cline/logs",
                        SourceToolDescriptor.LogFormat.JSONL, caps(true, true, true, true, false, false)),
                tier2(SourceTool.AIDER, "Aider", "aider", "~/.aider",
                        SourceToolDescriptor.LogFormat.JSONL, caps(true, true, true, true, false, false)),
                tier2(SourceTool.OPENCODE, "OpenCode", "opencode", "~/.opencode/sessions",
                        SourceToolDescriptor.LogFormat.EVENTS_JSONL, caps(true, true, true, true, false, true))
        );
    }

    public static List<SourceToolDescriptor> forTier(InstallationTier tier) {
        return all().stream().filter(d -> d.availableAt(tier)).toList();
    }

    private static SourceToolDescriptor core(
            SourceTool id, String label, String configType, String path,
            SourceToolDescriptor.LogFormat format, List<String> capabilities
    ) {
        return new SourceToolDescriptor(id, label, configType, InstallationTier.TIER_1_CORE, path, format, capabilities);
    }

    private static SourceToolDescriptor tier2(
            SourceTool id, String label, String configType, String path,
            SourceToolDescriptor.LogFormat format, List<String> capabilities
    ) {
        return new SourceToolDescriptor(id, label, configType, InstallationTier.TIER_2_ANALYTICS, path, format, capabilities);
    }

    private static SourceToolDescriptor tier3(
            SourceTool id, String label, String configType, String path,
            SourceToolDescriptor.LogFormat format, List<String> capabilities
    ) {
        return new SourceToolDescriptor(id, label, configType, InstallationTier.TIER_3_INTEGRATION, path, format, capabilities);
    }

    private static List<String> caps(
            boolean retries, boolean tokens, boolean edits, boolean tests, boolean subagents, boolean compaction
    ) {
        return List.of(
                "retries:" + retries,
                "tokens:" + tokens,
                "edits:" + edits,
                "tests:" + tests,
                "subagents:" + subagents,
                "compaction:" + compaction
        );
    }
}
