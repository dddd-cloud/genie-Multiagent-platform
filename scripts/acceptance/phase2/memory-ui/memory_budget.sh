#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}/ui"

echo "==> memory_budget"
pnpm exec vitest run \
  src/features/phase2/localMemory/__tests__/BudgetEarlySummaryRegressionTest.test.ts \
  src/features/phase2/localMemory/__tests__/LocalContextLimitRegressionTest.test.ts

echo "PASS: memory_budget"
exit 0
