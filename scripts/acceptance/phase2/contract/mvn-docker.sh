#!/usr/bin/env bash
# Docker-backed Maven launcher for hosts without a local Maven install.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
IMAGE="${GENIE_MAVEN_IMAGE:-maven:3.9.9-eclipse-temurin-21}"

# Git Bash on Windows needs //var/run/docker.sock; Linux uses /var/run/docker.sock.
DOCKER_SOCK="${GENIE_DOCKER_SOCK:-}"
if [[ -z "${DOCKER_SOCK}" ]]; then
  if [[ -S /var/run/docker.sock ]]; then
    DOCKER_SOCK="/var/run/docker.sock"
  else
    DOCKER_SOCK="//var/run/docker.sock"
  fi
fi

# Convert Git Bash path (/c/Users/...) to a Docker Desktop friendly Windows path.
MOUNT_ROOT="${ROOT_DIR}"
if command -v cygpath >/dev/null 2>&1; then
  MOUNT_ROOT="$(cygpath -m "${ROOT_DIR}")"
elif [[ "${ROOT_DIR}" =~ ^/([a-zA-Z])/(.*)$ ]]; then
  drive="$(echo "${BASH_REMATCH[1]}" | tr '[:lower:]' '[:upper:]')"
  MOUNT_ROOT="${drive}:/${BASH_REMATCH[2]}"
fi

# Prevent Git Bash from rewriting /workspace into a Windows path under Program Files\Git.
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

docker run --rm \
  -v "${MOUNT_ROOT}:/workspace" \
  -v "${DOCKER_SOCK}:/var/run/docker.sock" \
  -w "/workspace/genie-backend" \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  --add-host=host.docker.internal:host-gateway \
  "${IMAGE}" \
  mvn "$@"
