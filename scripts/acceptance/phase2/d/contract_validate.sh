#!/usr/bin/env bash
# Stage 2: validate UI MVP/Phase2 contracts and protect frozen paths.
# Does not exercise real E2E — see real_e2e.sh (PHASE2_REAL_E2E_READY=1).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}"

echo "==> D contract_validate: pnpm contract:validate"
(
  cd ui
  pnpm contract:validate
)

echo "==> D contract_validate: protected path git diff (must be clean vs HEAD)"
git diff --exit-code -- ui/src/contracts/phase2
git diff --exit-code -- ui/src/mocks/phase2/fixtures
git diff --exit-code -- docs/mvp-contract/phase2
git diff --exit-code -- ui/src/utils/querySSE.ts

echo "==> D contract_validate: make phase2-contract-acceptance"
make phase2-contract-acceptance

echo "PASS: contract_validate"
exit 0
