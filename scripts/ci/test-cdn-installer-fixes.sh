#!/usr/bin/env bash
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

# Cheap checks for CDN/installer P0/P1 fixes.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT}/.github/tier.env"
IMAGE_BASE="ghcr.io/arkaprava/$(basename "${ROOT}")"
cd "${ROOT}"

fail() { echo "FAIL: $*" >&2; exit 1; }

echo "== bash -n =="
bash -n scripts/ci/verify-agent-deb.sh
bash -n scripts/ci/render-cdn-compose.sh
bash -n scripts/ci/package-agent-ci.sh
bash -n scripts/ci/package-helm.sh
bash -n scripts/package-agent.sh
bash -n infra/installer/linux/postinst
bash -n infra/installer/linux/postinstall

echo "== postinst is self-contained =="
if grep -Eq '^[[:space:]]*exec[[:space:]]+.*postinstall' infra/installer/linux/postinst; then
  fail "postinst still wraps postinstall"
fi
grep -q 'UNIT_PATH' infra/installer/linux/postinst || fail "postinst missing UNIT_PATH"
grep -q 'systemctl --global enable' infra/installer/linux/postinst || fail "postinst missing systemctl --global enable"
grep -q '__SPRING_PROFILE__' infra/installer/linux/postinst || fail "postinst missing __SPRING_PROFILE__"
grep -q 'UNIT_PATH' infra/installer/linux/postinstall || fail "postinstall missing UNIT_PATH"
grep -q 'systemctl --global enable' infra/installer/linux/postinstall || fail "postinstall missing systemctl --global enable"

echo "== verify-agent-deb.sh against fake debs =="
WORK="$(mktemp -d "${TMPDIR:-/tmp}/retrysight-deb-test.XXXXXX")"
trap 'rm -rf "${WORK}"' EXIT

make_deb() {
  local dest="$1"
  local postinst_src="$2"
  local extra_postinstall="${3:-}"
  local pkg="${WORK}/pkgroot"
  rm -rf "${pkg}"
  mkdir -p "${pkg}/DEBIAN" "${pkg}/usr/lib"
  cat > "${pkg}/DEBIAN/control" <<'EOF'
Package: retrysight-agent
Version: 1.0.0
Architecture: amd64
Maintainer: test@retrysight.com
Description: test package
EOF
  cp "${postinst_src}" "${pkg}/DEBIAN/postinst"
  chmod 755 "${pkg}/DEBIAN/postinst"
  if [[ -n "${extra_postinstall}" ]]; then
    cp "${extra_postinstall}" "${pkg}/DEBIAN/postinstall"
    chmod 755 "${pkg}/DEBIAN/postinstall"
  fi
  if command -v dpkg-deb >/dev/null 2>&1; then
    dpkg-deb --build "${pkg}" "${dest}" >/dev/null
  else
    local ardir="${WORK}/ar"
    rm -rf "${ardir}"
    mkdir -p "${ardir}"
    echo '2.0' > "${ardir}/debian-binary"
    tar -C "${pkg}/DEBIAN" -czf "${ardir}/control.tar.gz" .
    tar -C "${pkg}" --exclude=DEBIAN -czf "${ardir}/data.tar.gz" .
    (cd "${ardir}" && ar r "${dest}" debian-binary control.tar.gz data.tar.gz >/dev/null)
  fi
}

INSTALLERS="${WORK}/release/agent-installers"
mkdir -p "${INSTALLERS}"

make_deb "${INSTALLERS}/retrysight-agent_1.0.0_amd64.deb" "${ROOT}/infra/installer/linux/postinst"
bash "${ROOT}/scripts/ci/verify-agent-deb.sh" "${INSTALLERS}/retrysight-agent_1.0.0_amd64.deb"

WRAPPER="${WORK}/wrapper-postinst"
cat > "${WRAPPER}" <<'EOF'
#!/bin/bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
exec bash "${DIR}/postinstall" "$@"
EOF
make_deb "${INSTALLERS}/wrapper.deb" "${WRAPPER}" "${ROOT}/infra/installer/linux/postinstall"
if out="$(bash "${ROOT}/scripts/ci/verify-agent-deb.sh" "${INSTALLERS}/wrapper.deb" 2>&1)"; then
  echo "${out}"
  fail "verify-agent-deb.sh should reject wrapper postinst"
fi
echo "OK: verify-agent-deb.sh rejects wrapper and accepts self-contained postinst"

echo "== render-cdn-compose.sh =="
VERSION="$(tr -d '[:space:]' < "${ROOT}/VERSION")"
COMPOSE="${WORK}/docker-compose.yml"
bash "${ROOT}/scripts/ci/render-cdn-compose.sh" "${COMPOSE}"
if grep -qE '^[[:space:]]*build:' "${COMPOSE}"; then
  fail "CDN compose has build:"
fi
if grep -qE '^[[:space:]]+agent:' "${COMPOSE}"; then
  fail "CDN compose has agent service"
fi
grep -q "${IMAGE_BASE}/postgres:${VERSION}" "${COMPOSE}" || fail "missing postgres image tag"
grep -q "${IMAGE_BASE}/manager:${VERSION}" "${COMPOSE}" || fail "missing manager image tag"
grep -q "${IMAGE_BASE}/frontend:${VERSION}" "${COMPOSE}" || fail "missing frontend image tag"
grep -q "${MANAGER_PORT}" "${COMPOSE}" || fail "missing manager port ${MANAGER_PORT}"
grep -q "${FRONTEND_PORT}" "${COMPOSE}" || fail "missing frontend port ${FRONTEND_PORT}"
grep -q 'pg_isready' "${COMPOSE}" || fail "missing postgres healthcheck"
grep -q 'auth/sso/status' "${COMPOSE}" || fail "missing manager healthcheck"
grep -q "${TIER_SLUG//-/_}_pgdata" "${COMPOSE}" || fail "missing named volume"
grep -q 'SECURITY_ALLOW_DEFAULT_SECRETS' "${COMPOSE}" || fail "missing SECURITY_ALLOW_DEFAULT_SECRETS"
grep -q 'CLICKHOUSE_ENABLED' "${COMPOSE}" || fail "missing CLICKHOUSE_ENABLED"
echo "OK: CDN compose"

echo "== stage-agent-cdn.mjs =="
node "${ROOT}/scripts/ci/stage-agent-cdn.test.mjs"

echo "== helm template defaults =="
if command -v helm >/dev/null 2>&1; then
  RENDERED="$(helm template retrysight "${ROOT}/helm/retrysight")"
  for component in postgres manager frontend; do
    echo "${RENDERED}" | grep -q "image: ${IMAGE_BASE}/${component}:" \
      || fail "helm template default ${component} image is not the public GHCR image"
  done
  helm lint "${ROOT}/helm/retrysight" >/dev/null
  echo "OK: helm template + lint"
else
  echo "SKIP: helm not installed"
fi

echo "All CDN/installer P0/P1 checks passed."
