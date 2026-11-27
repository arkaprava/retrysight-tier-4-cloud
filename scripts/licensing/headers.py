#!/usr/bin/env python3
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

"""Shared Apache-2.0 SPDX header definitions for RetrySight."""

from __future__ import annotations

COPYRIGHT_OWNER = "Arka Prava Ghosh"
COPYRIGHT_YEAR = "2026"
MARKER = "Copyright (c) 2026 Arka Prava Ghosh"

BLOCK_HEADER = f"""\
/*
 * Copyright (c) {COPYRIGHT_YEAR} {COPYRIGHT_OWNER}
 * SPDX-License-Identifier: Apache-2.0
 */
"""

HASH_HEADER = f"""\
# Copyright (c) {COPYRIGHT_YEAR} {COPYRIGHT_OWNER}
# SPDX-License-Identifier: Apache-2.0
"""

HTML_HEADER = f"""\
<!--
  Copyright (c) {COPYRIGHT_YEAR} {COPYRIGHT_OWNER}
  SPDX-License-Identifier: Apache-2.0
-->
"""

GRAPHQL_HEADER = BLOCK_HEADER

EXTENSIONS_BLOCK = {
    ".java",
    ".ts",
    ".tsx",
    ".js",
    ".mjs",
    ".cjs",
    ".css",
    ".graphql",
    ".gql",
}

EXTENSIONS_HASH = {".py", ".sh", ".yaml", ".yml", ".toml", ".properties"}

EXTENSIONS_HTML = {".html"}

SKIP_DIR_NAMES = {
    ".git",
    "node_modules",
    "target",
    "dist",
    "build",
    "coverage",
    "vendor",
    "__pycache__",
    ".idea",
    ".vscode",
    "canvases",
}

SKIP_FILE_NAMES = {
    "package-lock.json",
    "pnpm-lock.yaml",
    "yarn.lock",
}
