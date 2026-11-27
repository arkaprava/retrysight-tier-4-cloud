#!/usr/bin/env bash
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

# Build the agent jar and optional platform installers via jpackage.
# Native installers register OS auto-start (launchd / systemd user / Windows task).

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

# shellcheck source=/dev/null
if [[ -f "${ROOT}/.github/tier.env" ]]; then
  source "${ROOT}/.github/tier.env"
fi
# Agent profile is the tier slug (tier-core / tier-analytics / ...).
AGENT_PROFILE="${TIER_SLUG:-tier-core}"

VERSION="$(tr -d '[:space:]' < "${ROOT}/VERSION" 2>/dev/null || echo 0.0.0-dev)"
JPACKAGE_VERSION="$(printf '%s' "${VERSION}" | sed -E 's/^([0-9]+\.[0-9]+\.[0-9]+).*/\1/')"
if [[ ! "${JPACKAGE_VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || [[ "${JPACKAGE_VERSION}" =~ ^0\. ]]; then
  JPACKAGE_VERSION="1.0.0"
fi
INSTALL_ROOT="${RETRYSIGHT_AGENT_INSTALL_ROOT:-${HOME}/.retrysight-agent}"

stage_resources() {
  local src="$1"
  local dest="$2"
  rm -rf "${dest}"
  mkdir -p "${dest}"
  cp -R "${src}/." "${dest}/"
  # Bake the tier Spring profile into installer hooks before jpackage packs them.
  while IFS= read -r -d '' file; do
    if grep -q '__SPRING_PROFILE__' "${file}" 2>/dev/null; then
      # portable in-place replace (macOS + Linux)
      sed "s|__SPRING_PROFILE__|${AGENT_PROFILE}|g" "${file}" > "${file}.tmp"
      mv "${file}.tmp" "${file}"
    fi
  done < <(find "${dest}" -type f -print0)
}

echo "Building agent jar (profile=${AGENT_PROFILE})..."
mvn install -N -Dmaven.test.skip=true -B -q
mvn -pl agent/agent-app -am package -Dmaven.test.skip=true -B -q

JAR="$(ls "${ROOT}"/agent/agent-app/target/agent-app-*.jar | head -1)"
mkdir -p "${INSTALL_ROOT}"
cp "${JAR}" "${INSTALL_ROOT}/agent-app.jar"
echo "Staged jar -> ${INSTALL_ROOT}/agent-app.jar"

if ! command -v jpackage >/dev/null 2>&1; then
  echo "jpackage not found — jar staged only. Install JDK 21+ jpackage for native installers."
  exit 0
fi

OS="$(uname -s)"
OUTPUT_DIR="${ROOT}/release/agent-installers"
mkdir -p "${OUTPUT_DIR}"
STAGED_RES="$(mktemp -d "${TMPDIR:-/tmp}/retrysight-jpackage-res.XXXXXX")"
cleanup() { rm -rf "${STAGED_RES}"; }
trap cleanup EXIT

JPACKAGE_COMMON=(
  --input "${INSTALL_ROOT}"
  --main-jar agent-app.jar
  --main-class org.springframework.boot.loader.launch.JarLauncher
  --name RetrySightAgent
  --app-version "${JPACKAGE_VERSION}"
  --vendor "RetrySight"
  --copyright "Copyright (c) 2026 Arka Prava Ghosh"
  --description "RetrySight fleet collector agent (${AGENT_PROFILE})"
  --java-options "-Dspring.main.web-application-type=none"
  --java-options "-Dagent.platform.auto-discovery=true"
  --java-options "-Dspring.profiles.active=${AGENT_PROFILE}"
)

echo "Building native installer for ${OS}..."
case "${OS}" in
  Darwin)
    stage_resources "${ROOT}/infra/installer/macos" "${STAGED_RES}/macos"
    chmod +x "${STAGED_RES}/macos/postinstall" 2>/dev/null || true
    EXTRA=(--resource-dir "${STAGED_RES}/macos")
    if [[ -f "${STAGED_RES}/macos/entitlements.plist" ]]; then
      EXTRA+=(--mac-entitlements "${STAGED_RES}/macos/entitlements.plist")
    fi
    jpackage "${JPACKAGE_COMMON[@]}" "${EXTRA[@]}" \
      --type pkg \
      --dest "${OUTPUT_DIR}" \
      --mac-package-identifier "com.retrysight.agent.${AGENT_PROFILE}"
    # jpackage emits a thin Mach-O pkg (arm64 or x64). Rename so CDN/CI
    # can ship two architecture-specific installers instead of a fake "universal".
    macos_arch="$(uname -m)"
    case "${macos_arch}" in
      arm64|aarch64) macos_arch="arm64" ;;
      x86_64) macos_arch="x64" ;;
    esac
    pkg="$(ls "${OUTPUT_DIR}"/RetrySightAgent-*.pkg 2>/dev/null | head -1 || true)"
    if [[ -n "${pkg}" ]]; then
      pkg_base="$(basename "${pkg}" .pkg)"
      if [[ "${pkg_base}" != *-arm64 && "${pkg_base}" != *-x64 && "${pkg_base}" != *-amd64 ]]; then
        dest="${OUTPUT_DIR}/${pkg_base}-${macos_arch}.pkg"
        mv "${pkg}" "${dest}"
        echo "Renamed $(basename "${pkg}") -> $(basename "${dest}")"
      fi
    fi
    ;;
  Linux)
    stage_resources "${ROOT}/infra/installer/linux" "${STAGED_RES}/linux"
    chmod +x "${STAGED_RES}/linux/postinstall" "${STAGED_RES}/linux/postinst" \
      "${STAGED_RES}/linux/prerm" "${STAGED_RES}/linux/postrm" 2>/dev/null || true
    jpackage "${JPACKAGE_COMMON[@]}" \
      --resource-dir "${STAGED_RES}/linux" \
      --type deb \
      --dest "${OUTPUT_DIR}" \
      --linux-package-name retrysight-agent \
      --linux-deb-maintainer "contact@retrysight.com" \
      --linux-app-category utils
    ;;
  MINGW*|MSYS*|CYGWIN*|Windows_NT)
    stage_resources "${ROOT}/infra/installer/windows" "${STAGED_RES}/windows"
    jpackage "${JPACKAGE_COMMON[@]}" \
      --resource-dir "${STAGED_RES}/windows" \
      --type msi \
      --dest "${OUTPUT_DIR}" \
      --win-menu \
      --win-shortcut \
      --win-per-user-install \
      --win-menu-group "RetrySight" \
      --app-content "${STAGED_RES}/windows/register-autostart.ps1"
    ;;
  *)
    echo "Unsupported OS for jpackage: ${OS}" >&2
    exit 1
    ;;
esac

echo "Installers written to ${OUTPUT_DIR}"
echo "Auto-start: macOS LaunchAgent / Linux user systemd; Windows ships register-autostart.ps1 for AtLogOn task (profile=)."

if [[ "${OS}" == "Darwin" && "${MACOS_SIGN:-0}" == "1" && -x "${ROOT}/scripts/sign-macos-agent.sh" ]]; then
  PKG="$(ls "${OUTPUT_DIR}"/*.pkg 2>/dev/null | head -1 || true)"
  if [[ -n "${PKG}" ]]; then
    echo "Signing and notarizing ${PKG}..."
    "${ROOT}/scripts/sign-macos-agent.sh" "${PKG}"
  fi
fi
