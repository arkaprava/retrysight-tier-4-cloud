#!/usr/bin/env bash
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

# Generate docs/FUNCTIONAL_SPECIFICATION.pdf from markdown + Mermaid diagrams.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DOCS="$ROOT/docs"
DIAGRAMS="$DOCS/diagrams"
OUT_PDF="$DOCS/FUNCTIONAL_SPECIFICATION.pdf"
MD="$DOCS/FUNCTIONAL_SPECIFICATION.md"

cd "$ROOT"

echo "==> Rendering Mermaid diagrams (mermaid.ink)"
for mmd in "$DIAGRAMS"/*.mmd; do
  base="$(basename "$mmd" .mmd)"
  out="$DIAGRAMS/${base}.png"
  tmp="$DIAGRAMS/${base}.tmp"
  encoded="$(python3 -c "import base64, pathlib; print(base64.urlsafe_b64encode(pathlib.Path('$mmd').read_bytes()).decode())")"
  curl --max-time 45 -fsSL "https://mermaid.ink/img/${encoded}" -o "$tmp"
  # mermaid.ink returns JPEG bytes with a .png name — normalize for typst/pandoc
  sips -s format png "$tmp" --out "$out" >/dev/null
  rm -f "$tmp"
  echo "    ${base}.png"
done

echo "==> Building PDF with pandoc (typst engine)"
if ! command -v pandoc >/dev/null 2>&1; then
  echo "pandoc is required (brew install pandoc typst)" >&2
  exit 1
fi

cd "$DOCS"
pandoc FUNCTIONAL_SPECIFICATION.md \
  -o FUNCTIONAL_SPECIFICATION.pdf \
  --pdf-engine=typst \
  --toc \
  --toc-depth=2 \
  --metadata title="RetrySight Platform — Functional Specification" \
  --metadata author="Arka Prava Ghosh" \
  --metadata date="July 2026"

echo "==> Done: $OUT_PDF"
ls -lh "$OUT_PDF"
