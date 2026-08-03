#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}"

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

echo "==> Phase2 C0 contract acceptance"
require_cmd java
require_cmd mvn
require_cmd node
require_cmd pnpm
require_cmd sha256sum

echo "==> 1/8 verify protected baseline"
bash scripts/acceptance/phase2/contract/verify_protected_baseline.sh || overall_fail

echo "==> 2-5/8 backend Phase2 + existing contract/security tests"
(
  cd genie-backend
  mvn -q \
    -Dtest=\
Phase2ContractShapeTest,\
Phase2ContractSerializationTest,\
Phase2CapabilityKeysTest,\
Phase2ErrorCodeContractTest,\
Phase2FakePortContractTest,\
Phase2SnapshotCompatibilityTest,\
Phase2FinalAnswerCompatibilityTest,\
Phase2SecurityIntegrationTest,\
ContractShapeTest,\
ContractSerializationTest,\
ContractFakeSupportTest,\
SecurityCsrfIntegrationTest,\
InternalAgentSecurityIntegrationTest \
    test
) || overall_fail

echo "==> 6/8 frontend contract:validate"
(
  cd ui
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
