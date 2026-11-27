#!/usr/bin/env python3
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

"""Fail if first-party source files are missing the Apache-2.0 SPDX copyright header."""

from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from headers import MARKER  # noqa: E402


def _load_add_module():
    path = HERE / "add-copyright-headers.py"
    spec = importlib.util.spec_from_file_location("add_copyright_headers", path)
    mod = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(mod)
    return mod


def main() -> int:
    add = _load_add_module()
    missing: list[Path] = []
    for path in add.iter_files():
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        if MARKER not in text[:2000]:
            missing.append(path)
    if missing:
        print(f"Missing copyright header in {len(missing)} file(s):")
        for path in missing[:100]:
            print(f"  {path.relative_to(ROOT)}")
        if len(missing) > 100:
            print(f"  ... and {len(missing) - 100} more")
        return 1
    print("All scanned first-party sources include the copyright marker.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
