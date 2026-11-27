#!/usr/bin/env bash
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

# Verify the Linux agent .deb ships a self-contained Debian postinst.
# jpackage control.tar only includes postinst/preinst/prerm/postrm — a wrapper
# that execs a sibling postinstall will fail at dpkg -i.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
INSTALLERS="${ROOT}/release/agent-installers"

DEB="${1:-}"
if [[ -z "${DEB}" ]]; then
  shopt -s nullglob
  for candidate in "${INSTALLERS}"/retrysight-agent_*.deb "${INSTALLERS}"/*.deb; do
    if [[ -f "${candidate}" ]]; then
      DEB="${candidate}"
      break
    fi
  done
  shopt -u nullglob
elif [[ ! -f "${DEB}" ]]; then
  echo "No .deb at ${DEB}" >&2
  exit 1
fi

if [[ -z "${DEB}" ]]; then
  echo "No .deb found under ${INSTALLERS}" >&2
  exit 1
fi

TMP="$(mktemp -d "${TMPDIR:-/tmp}/retrysight-verify-deb.XXXXXX")"
cleanup() { rm -rf "${TMP}"; }
trap cleanup EXIT

CONTROL_DIR="${TMP}/control"
mkdir -p "${CONTROL_DIR}"

extract_control() {
  if command -v dpkg-deb >/dev/null 2>&1; then
    dpkg-deb --control "${DEB}" "${CONTROL_DIR}"
    return 0
  fi

  local work="${TMP}/ar"
  mkdir -p "${work}"
  (cd "${work}" && ar x "${DEB}")
  local archive=""
  archive="$(ls "${work}"/control.tar.* 2>/dev/null | head -1 || true)"
  if [[ -z "${archive}" ]]; then
    echo "No control.tar.* in ${DEB}" >&2
    return 1
  fi
  case "${archive}" in
    *.zst|*.zstd)
      if command -v zstd >/dev/null 2>&1; then
        zstd -d -c "${archive}" | tar -C "${CONTROL_DIR}" -xf -
      else
        tar -C "${CONTROL_DIR}" --zstd -xf "${archive}"
      fi
      ;;
    *.xz) tar -C "${CONTROL_DIR}" -xJf "${archive}" ;;
    *.gz) tar -C "${CONTROL_DIR}" -xzf "${archive}" ;;
    *) tar -C "${CONTROL_DIR}" -xf "${archive}" ;;
  esac
}

extract_control

POSTINST=""
for candidate in "${CONTROL_DIR}/postinst" "${CONTROL_DIR}/./postinst"; do
  if [[ -f "${candidate}" ]]; then
    POSTINST="${candidate}"
    break
  fi
done
# Some archives unpack as ./postinst relative to CONTROL_DIR already found above;
# also accept a nested find if jpackage layout differs.
if [[ -z "${POSTINST}" ]]; then
  POSTINST="$(find "${CONTROL_DIR}" -name postinst -type f | head -1 || true)"
fi

if [[ -z "${POSTINST}" || ! -f "${POSTINST}" ]]; then
  echo "FAIL: postinst missing from control archive of ${DEB}" >&2
  exit 1
fi

if grep -Eq '^[[:space:]]*exec[[:space:]]+.*postinstall' "${POSTINST}"; then
  echo "FAIL: postinst still execs a sibling postinstall (wrapper) in ${DEB}" >&2
  exit 1
fi

if ! grep -q 'UNIT_PATH' "${POSTINST}"; then
  echo "FAIL: postinst does not mention UNIT_PATH in ${DEB}" >&2
  exit 1
fi

if ! grep -q 'systemctl --global enable' "${POSTINST}"; then
  echo "FAIL: postinst does not mention systemctl --global enable in ${DEB}" >&2
  exit 1
fi

POSTINSTALL_IN_CONTROL="$(find "${CONTROL_DIR}" -name postinstall -type f | head -1 || true)"
if [[ -n "${POSTINSTALL_IN_CONTROL}" ]] && grep -Eq '^[[:space:]]*exec[[:space:]]+.*postinstall' "${POSTINST}"; then
  echo "FAIL: wrapper pattern — postinstall is in the control archive and postinst execs it" >&2
  exit 1
fi

echo "OK: $(basename "${DEB}") postinst is self-contained (systemd user unit)."
