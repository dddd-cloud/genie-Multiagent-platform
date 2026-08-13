#!/usr/bin/env bash
# Shared helpers for the Stage 6 Agent independent acceptance scenario scripts.
# Sourcing this file must not print anything to stdout.
set -euo pipefail

AGENT_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AGENT_ROOT_DIR="$(cd "${AGENT_SCRIPT_DIR}/../../../.." && pwd)"
AGENT_MVN_DOCKER="${AGENT_ROOT_DIR}/scripts/acceptance/phase2/contract/mvn-docker.sh"
AGENT_LOGS_DIR="${AGENT_SCRIPT_DIR}/logs"

run_scenario() {
  local name="$1"
  local tests="$2"
  if [[ ! -f "${AGENT_MVN_DOCKER}" ]]; then
    printf '{"name":"%s","result":"FAIL"}\n' "${name}"
    exit 2
  fi
  mkdir -p "${AGENT_LOGS_DIR}"
  if bash "${AGENT_MVN_DOCKER}" "-Djacoco.skip=true" "-Dtest=${tests}" test \
      >"${AGENT_LOGS_DIR}/${name}.log" 2>&1; then
    printf '{"name":"%s","result":"PASS"}\n' "${name}"
    exit 0
  fi
  printf '{"name":"%s","result":"FAIL"}\n' "${name}"
  exit 1
}
