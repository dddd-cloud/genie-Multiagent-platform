#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}/ui"

echo "==> D local_memory_user_isolation"
pnpm vitest run \
  src/features/phase2/localMemory/__tests__/UserScopedMemoryPathTest.test.ts \
  src/features/phase2/localMemory/__tests__/MemoryTaskQueueAccountSwitchTest.test.ts

echo "PASS: local_memory_user_isolation"
exit 0
