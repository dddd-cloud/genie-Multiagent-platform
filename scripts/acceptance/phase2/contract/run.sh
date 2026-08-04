#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MVN_DOCKER="${SCRIPT_DIR}/mvn-docker.sh"
MVN_CMD=()

overall_blocked() {
  echo "Overall: BLOCKED"
  exit 2
}

overall_fail() {
  echo "Overall: FAIL"
  exit 1
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "BLOCKED: missing required command: $1"
    overall_blocked
  fi
}

resolve_maven() {
  if command -v mvn >/dev/null 2>&1; then
    MVN_CMD=(mvn)
    echo "Using local Maven: $(command -v mvn)"
    return 0
  fi
  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    if [[ ! -x "${MVN_DOCKER}" && ! -f "${MVN_DOCKER}" ]]; then
      echo "BLOCKED: missing ${MVN_DOCKER}"
      overall_blocked
    fi
    MVN_CMD=(bash "${MVN_DOCKER}")
    echo "Using Docker Maven launcher: ${MVN_DOCKER}"
    return 0
  fi
  echo "BLOCKED: neither local mvn nor usable docker is available"
  overall_blocked
}

run_maven_tests() {
  local -a args=(
    -q
    -Dtest=Phase2ContractShapeTest,Phase2ContractSerializationTest,Phase2CapabilityKeysTest,Phase2ErrorCodeContractTest,Phase2FakePortContractTest,Phase2ReusablePortContractTest,Phase2SnapshotCompatibilityTest,Phase2FinalAnswerCompatibilityTest,Phase2SecurityIntegrationTest,ContractShapeTest,ContractSerializationTest,ContractFakeSupportTest,SecurityCsrfIntegrationTest,InternalAgentSecurityIntegrationTest
    test
  )
  if [[ "${MVN_CMD[0]}" == "mvn" ]]; then
    (
      cd genie-backend
      "${MVN_CMD[@]}" "${args[@]}"
    )
  else
    "${MVN_CMD[@]}" "${args[@]}"
  fi
}

echo "==> Phase2 C0 contract acceptance"
require_cmd git
require_cmd node
require_cmd pnpm
require_cmd sha256sum
resolve_maven

echo "==> 1/8 verify protected baseline"
bash scripts/acceptance/phase2/contract/verify_protected_baseline.sh || overall_fail

echo "==> 2-5/8 backend Phase2 + existing contract/security tests"
run_maven_tests || overall_fail

echo "==> 6/8 frontend contract:validate"
(
  cd ui
  pnpm install --frozen-lockfile
  pnpm contract:validate
) || overall_fail

echo "==> 7/8 frontend typecheck"
(
  cd ui
  pnpm typecheck
) || overall_fail

echo "==> 8/8 frontend Vitest"
(
  cd ui
  pnpm test
) || overall_fail

echo "Overall: PASS"
exit 0
