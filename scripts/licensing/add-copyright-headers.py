#!/usr/bin/env python3
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

"""Insert Apache-2.0 SPDX copyright headers into first-party source files."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent))

from headers import (  # noqa: E402
    BLOCK_HEADER,
    EXTENSIONS_BLOCK,
    EXTENSIONS_HASH,
    EXTENSIONS_HTML,
    HASH_HEADER,
    HTML_HEADER,
    MARKER,
    SKIP_DIR_NAMES,
    SKIP_FILE_NAMES,
)

OLD_MARKER = "Copyright (c) 2026 Arkaprava Ghosh"

INCLUDE_ROOTS = [
    ROOT / "shared",
    ROOT / "manager",
    ROOT / "agent",
    ROOT / "ui" / "manager-dashboard" / "src",
    ROOT / "mcp" / "packages",
    ROOT / "mcp" / "ide-config",
    ROOT / "scripts",
    ROOT / "infra" / "scripts",
    ROOT / "infra" / "systemd",
    ROOT / "infra" / "launchd",
]


def header_for(path: Path) -> str | None:
    ext = path.suffix.lower()
    if ext in EXTENSIONS_BLOCK:
        return BLOCK_HEADER
    if ext in EXTENSIONS_HASH:
        return HASH_HEADER
    if ext in EXTENSIONS_HTML:
        return HTML_HEADER
    return None


def strip_block_header(text: str) -> str:
    stripped = text.lstrip("\ufeff")
    if not stripped.startswith("/*"):
        return text
    end = stripped.find("*/")
    if end == -1 or (MARKER not in stripped[: end + 2] and OLD_MARKER not in stripped[: end + 2]):
        return text
    rest = stripped[end + 2 :]
    return rest[1:] if rest.startswith("\n") else rest


def strip_hash_header(text: str) -> str:
    lines = text.splitlines(keepends=True)
    if not lines:
        return text
    shebang = ""
    i = 0
    if lines[0].startswith("#!"):
        shebang = lines[0]
        i = 1
    saw = False
    while i < len(lines):
        line = lines[i]
        if not line.startswith("#"):
            break
        content = line[1:].strip()
        if not content:
            if saw:
                i += 1
                continue
            break
        if any(
            key in content
            for key in (
                MARKER,
                "Copyright (c) 2026 Arkaprava Ghosh",
                "Proprietary and confidential",  # legacy header, replaced on rewrite
                "SPDX-License-Identifier",
                "Unauthorized copying",
                "See the LICENSE file",
                "modification, or use of this source",
            )
        ):
            saw = True
            i += 1
            continue
        break
    if not saw:
        return text
    return shebang + "".join(lines[i:])


def strip_html_header(text: str) -> str:
    stripped = text.lstrip("\ufeff")
    if not stripped.startswith("<!--"):
        return text
    end = stripped.find("-->")
    if end == -1 or (MARKER not in stripped[: end + 3] and OLD_MARKER not in stripped[: end + 3]):
        return text
    rest = stripped[end + 3 :]
    return rest[1:] if rest.startswith("\n") else rest


def compose(header: str, body: str) -> str:
    if body.startswith("\n"):
        return header + body
    return header + "\n" + body


def apply_header(path: Path) -> bool:
    header = header_for(path)
    if header is None or path.name in SKIP_FILE_NAMES:
        return False
    try:
        original = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return False

    ext = path.suffix.lower()
    if ext in EXTENSIONS_BLOCK:
        # A shebang (#!/usr/bin/env node) must stay on line 1 or the script will not run.
        shebang = ""
        body = original
        if body.startswith("#!"):
            nl = body.find("\n")
            shebang, body = (body + "\n", "") if nl == -1 else (body[: nl + 1], body[nl + 1 :])
        new_text = shebang + compose(header, strip_block_header(body.lstrip("\n")))
    elif ext in EXTENSIONS_HASH:
        body = strip_hash_header(original)
        if body.startswith("#!"):
            nl = body.find("\n")
            if nl == -1:
                new_text = body + "\n" + header
            else:
                rest = body[nl + 1 :]
                new_text = body[: nl + 1] + header + ("" if rest.startswith("\n") else "\n") + rest
        else:
            new_text = compose(header, body)
    else:
        new_text = compose(header, strip_html_header(original))

    if new_text == original:
        return False
    path.write_text(new_text, encoding="utf-8")
    return True


def iter_files():
    seen: set[Path] = set()
    for root in INCLUDE_ROOTS:
        if not root.exists():
            continue
        paths = [root] if root.is_file() else root.rglob("*")
        for path in paths:
            if not path.is_file():
                continue
            if any(part in SKIP_DIR_NAMES for part in path.parts):
                continue
            if path in seen or header_for(path) is None:
                continue
            seen.add(path)
            yield path


def main() -> int:
    changed = 0
    scanned = 0
    for path in sorted(iter_files()):
        scanned += 1
        if apply_header(path):
            changed += 1
            print(f"updated {path.relative_to(ROOT)}")
    print(f"scanned={scanned} updated={changed}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
