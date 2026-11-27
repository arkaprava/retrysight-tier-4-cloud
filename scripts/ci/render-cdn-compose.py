#!/usr/bin/env python3
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

"""Render the pull-only docker-compose.yml published to the CDN and GitHub Releases.

Derived from the repository's own docker-compose.yml so it never drifts:
  * `build:` blocks are removed,
  * first-party images (retrysight/<tier>-<component>:...) point at the public GHCR image,
  * the optional `agent` service is dropped (it bind-mounts repo files; install the native agent).

Usage: render-cdn-compose.py <version> <ghcr image repo> <out file>
"""
import re
import sys
from pathlib import Path

version, image_repo, out = sys.argv[1], sys.argv[2].lower(), Path(sys.argv[3])
root = Path(__file__).resolve().parents[2]
lines = (root / "docker-compose.yml").read_text().splitlines()
# drop the source file's leading comment block (it describes building from source)
while lines and (lines[0].startswith("#") or not lines[0].strip()):
    lines.pop(0)

result, service, skip_indent = [], None, None
for line in lines:
    stripped = line.strip()
    indent = len(line) - len(line.lstrip(" "))

    if skip_indent is not None:
        if stripped == "" or indent > skip_indent:
            continue
        skip_indent = None

    m = re.match(r"^  ([A-Za-z0-9_-]+):\s*$", line)
    if m and indent == 2:
        service = m.group(1)
        if service == "agent":
            skip_indent = 2
            continue
    elif re.match(r"^[A-Za-z]", line):
        service = None

    if service and re.match(r"^\s+build:\s*", line):
        if stripped == "build:":
            skip_indent = indent
        continue

    img = re.match(r"^(\s+image:\s*)retrysight/[a-z0-9-]+-(postgres|manager|frontend|agent):\S+\s*$", line)
    if img:
        line = f"{img.group(1)}ghcr.io/{image_repo}/{img.group(2)}:{version}"

    result.append(line)

text = "\n".join(result).rstrip() + "\n"
header = (
    f"# RetrySight {version} — generated from docker-compose.yml for prebuilt images.\n"
    "# Uses dev-default credentials unless you set them in a .env file next to this one.\n"
    "# Change them before exposing this stack to anyone else.\n"
)
if re.search(r"^\s+build:", text, re.M):
    sys.exit("generated compose must not contain build:")
if re.search(r"^  agent:\s*$", text, re.M):
    sys.exit("generated compose must not contain the agent service")
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(header + text)
print(f"Wrote {out}")
