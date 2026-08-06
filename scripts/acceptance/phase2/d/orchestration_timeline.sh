#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}/ui"

echo "==> D orchestration_timeline"
pnpm vitest run \
  src/features/phase2/orchestration/__tests__/OrchestrationReducerTest.test.ts \
  src/features/phase2/orchestration/__tests__/OrchestrationDuplicateSequenceTest.test.ts \
  src/features/phase2/orchestration/__tests__/SnapshotOrchestrationHydrateTest.test.ts

echo "PASS: orchestration_timeline"
exit 0
