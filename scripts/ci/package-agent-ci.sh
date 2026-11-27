#!/usr/bin/env bash
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

# CI wrapper for native agent installers (Linux .deb, Windows .msi, macOS .pkg).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

VERSION="$(tr -d '[:space:]' < "${ROOT}/VERSION" 2>/dev/null || echo 0.0.0-dev)"
OUTPUT_DIR="${ROOT}/release/agent-installers"
mkdir -p "${OUTPUT_DIR}"

export RETRYSIGHT_AGENT_INSTALL_ROOT="${ROOT}/.ci/agent-staging"

# Windows runners need WiX for jpackage MSI output.
if [[ "${RUNNER_OS:-}" == "Windows" ]]; then
  if command -v choco >/dev/null 2>&1; then
    choco install wixtoolset --no-progress -y || true
  fi
fi

./scripts/package-agent.sh

if [[ "$(uname -s)" == "Linux" ]]; then
  bash "${ROOT}/scripts/ci/verify-agent-deb.sh"
fi

# Stage portable jar for users who prefer manual install.
JAR="$(ls "${ROOT}"/agent/agent-app/target/agent-app-*.jar 2>/dev/null | head -1 || true)"
if [[ -n "${JAR}" ]]; then
  cp "${JAR}" "${OUTPUT_DIR}/retrysight-agent-${VERSION}.jar"
fi

echo "Agent artifacts in ${OUTPUT_DIR}:"
ls -la "${OUTPUT_DIR}" || true
