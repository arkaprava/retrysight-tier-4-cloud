#!/usr/bin/env bash
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

# Build the tier's Docker images (DOCKER_COMPONENTS in .github/tier.env) with buildx.
#
#   PUSH_IMAGES=true    push multi-arch images to ${REGISTRY}/${IMAGE_REPO}/<component>:<version>
#                       (and :latest for stable versions)
#   EXPORT_IMAGES=true  write per-arch `docker save` tarballs to release/docker-export/
#                       retrysight-<component>-<version>-linux-<arch>.tar.gz, tagged with the
#                       same GHCR reference so `docker load` + the CDN compose file work offline
#   neither             build linux/amd64 only, to check that the images build (PRs)
#
# Requires the manager jar (mvn package) to exist; the frontend builds inside Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"
source "${ROOT}/.github/tier.env"

VERSION="${RETRYSIGHT_VERSION:-$(tr -d '[:space:]' < "${ROOT}/VERSION" 2>/dev/null || echo 0.0.0-dev)}"
REGISTRY="${REGISTRY:-ghcr.io}"
IMAGE_REPO="$(printf '%s' "${IMAGE_REPO:-${GITHUB_REPOSITORY:-arkaprava/$(basename "${ROOT}")}}" | tr '[:upper:]' '[:lower:]')"
PLATFORMS="${PLATFORMS:-linux/amd64,linux/arm64}"
PUSH="${PUSH_IMAGES:-false}"
EXPORT="${EXPORT_IMAGES:-false}"
SOURCE_URL="https://github.com/${IMAGE_REPO}"
OUT_DIR="${ROOT}/release/docker-export"

dockerfile_args() {
  case "$1" in
    postgres) echo "-f docker/postgres/Dockerfile docker/postgres" ;;
    manager)  echo "-f infra/manager/Dockerfile ." ;;
    frontend) echo "-f infra/frontend/Dockerfile ." ;;
    agent)    echo "-f infra/agent/Dockerfile ." ;;
    *) echo "Unknown component: $1" >&2; return 1 ;;
  esac
}

is_stable() { [[ "${VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; }

labels=(
  --label "org.opencontainers.image.source=${SOURCE_URL}"
  --label "org.opencontainers.image.version=${VERSION}"
  --label "org.opencontainers.image.licenses=Apache-2.0"
  --label "org.opencontainers.image.title=RetrySight ${TIER_SLUG}"
)

mkdir -p "${ROOT}/release"
: > "${ROOT}/release/docker-images.txt"
[[ "${EXPORT}" == "true" ]] && mkdir -p "${OUT_DIR}"

for component in ${DOCKER_COMPONENTS}; do
  image="${REGISTRY}/${IMAGE_REPO}/${component}"
  # shellcheck disable=SC2046
  set -- $(dockerfile_args "${component}")
  echo "${image}:${VERSION}" >> "${ROOT}/release/docker-images.txt"

  if [[ "${PUSH}" == "true" ]]; then
    tags=(-t "${image}:${VERSION}")
    is_stable && tags+=(-t "${image}:latest")
    echo "==> ${component}: build + push ${PLATFORMS}"
    docker buildx build --platform "${PLATFORMS}" "${tags[@]}" "${labels[@]}" --push "$@"
  fi

  if [[ "${EXPORT}" == "true" ]]; then
    IFS=',' read -r -a platform_list <<< "${PLATFORMS}"
    for platform in "${platform_list[@]}"; do
      arch="${platform#linux/}"
      out="${OUT_DIR}/retrysight-${component}-${VERSION}-linux-${arch}.tar.gz"
      echo "==> ${component}: export ${platform} -> $(basename "${out}")"
      # --load + docker save works with every buildx driver (the docker exporter does not).
      docker buildx build --platform "${platform}" -t "${image}:${VERSION}" "${labels[@]}" --load "$@"
      docker save "${image}:${VERSION}" | gzip -c > "${out}"
    done
  fi

  if [[ "${PUSH}" != "true" && "${EXPORT}" != "true" ]]; then
    echo "==> ${component}: build check (linux/amd64)"
    docker buildx build --platform linux/amd64 -t "${image}:${VERSION}" "${labels[@]}" "$@"
  fi
done

echo "Images:"
cat "${ROOT}/release/docker-images.txt"
