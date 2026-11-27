#!/usr/bin/env bash
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

# Sign and notarize a macOS agent installer produced by scripts/package-agent.sh.
#
# Required environment (release builds):
#   MACOS_APP_SIGN_IDENTITY   Developer ID Application: Your Name (TEAMID)
#   MACOS_INSTALLER_SIGN_IDENTITY  Developer ID Installer: Your Name (TEAMID)
#   APPLE_NOTARY_PROFILE      notarytool keychain profile (preferred)
#     OR APPLE_ID + APPLE_TEAM_ID + APPLE_APP_PASSWORD
#
# Usage:
#   ./scripts/sign-macos-agent.sh release/agent-installers/RetrySightAgent-1.0.0.pkg

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="${1:-}"
if [[ -z "${PKG}" ]]; then
  PKG="$(ls "${ROOT}"/release/agent-installers/*.pkg 2>/dev/null | head -1 || true)"
fi
if [[ -z "${PKG}" || ! -f "${PKG}" ]]; then
  echo "Usage: $0 <unsigned.pkg>" >&2
  exit 1
fi

APP_SIGN="${MACOS_APP_SIGN_IDENTITY:-}"
INSTALLER_SIGN="${MACOS_INSTALLER_SIGN_IDENTITY:-}"
ENTITLEMENTS="${ROOT}/infra/installer/macos/entitlements.plist"
SIGNED_PKG="${PKG%.pkg}.signed.pkg"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "macOS signing requires Darwin" >&2
  exit 1
fi

if [[ -z "${APP_SIGN}" || -z "${INSTALLER_SIGN}" ]]; then
  echo "Set MACOS_APP_SIGN_IDENTITY and MACOS_INSTALLER_SIGN_IDENTITY for signing." >&2
  echo "Skipping sign/notarize — pkg left unsigned at ${PKG}" >&2
  exit 0
fi

WORK="$(mktemp -d "${TMPDIR:-/tmp}/retrysight-sign.XXXXXX")"
trap 'rm -rf "${WORK}"' EXIT

echo "Expanding ${PKG}..."
pkgutil --expand-full "${PKG}" "${WORK}/expanded"
APP="$(find "${WORK}/expanded" -name '*.app' -maxdepth 4 | head -1 || true)"
if [[ -n "${APP}" ]]; then
  echo "Signing app bundle ${APP}..."
  codesign --force --options runtime --timestamp \
    --entitlements "${ENTITLEMENTS}" \
    --sign "${APP_SIGN}" \
    "${APP}"
  codesign --verify --deep --strict --verbose=2 "${APP}"
fi

UNSIGNED="${WORK}/unsigned.pkg"
echo "Repacking pkg..."
pkgutil --flatten "${WORK}/expanded" "${UNSIGNED}"
productsign --sign "${INSTALLER_SIGN}" "${UNSIGNED}" "${SIGNED_PKG}"
pkgutil --check-signature "${SIGNED_PKG}"

echo "Submitting for notarization..."
if [[ -n "${APPLE_NOTARY_PROFILE:-}" ]]; then
  xcrun notarytool submit "${SIGNED_PKG}" --keychain-profile "${APPLE_NOTARY_PROFILE}" --wait
else
  : "${APPLE_ID:?Set APPLE_ID or APPLE_NOTARY_PROFILE}"
  : "${APPLE_TEAM_ID:?Set APPLE_TEAM_ID or APPLE_NOTARY_PROFILE}"
  : "${APPLE_APP_PASSWORD:?Set APPLE_APP_PASSWORD or APPLE_NOTARY_PROFILE}"
  xcrun notarytool submit "${SIGNED_PKG}" \
    --apple-id "${APPLE_ID}" \
    --team-id "${APPLE_TEAM_ID}" \
    --password "${APPLE_APP_PASSWORD}" \
    --wait
fi

echo "Stapling ticket..."
xcrun stapler staple "${SIGNED_PKG}"
spctl --assess --type install --verbose=4 "${SIGNED_PKG}" || true

echo "Signed and notarized pkg -> ${SIGNED_PKG}"
