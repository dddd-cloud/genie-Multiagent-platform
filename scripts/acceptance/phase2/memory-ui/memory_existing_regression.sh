#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}/ui"

echo "==> memory_existing_regression"
pnpm exec vitest run \
  src/features/phase2/localMemory/__tests__/MemoryMarkdownParserTest.test.ts \
  src/features/phase2/localMemory/__tests__/MemoryMarkdownSerializerTest.test.ts \
  src/features/phase2/localMemory/__tests__/MemoryPatchValidatorTest.test.ts \
  src/features/phase2/localMemory/__tests__/MemoryTaskQueueAccountSwitchTest.test.ts \
  src/features/phase2/localMemory/__tests__/OpfsUnavailableTest.test.ts \
  src/features/phase2/localMemory/__tests__/OpfsWriteVerificationTest.test.ts \
  src/features/phase2/localMemory/__tests__/UserScopedMemoryPathTest.test.ts \
  src/features/phase2/localMemory/__tests__/FiveTurnSummaryRegressionTest.test.ts

echo "PASS: memory_existing_regression"
exit 0
