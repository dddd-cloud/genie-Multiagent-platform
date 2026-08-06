#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}/ui"

echo "==> D opfs_unavailable_corruption"
pnpm vitest run \
  src/features/phase2/localMemory/__tests__/OpfsUnavailableTest.test.ts \
  src/features/phase2/localMemory/__tests__/OpfsWriteVerificationTest.test.ts \
  src/features/phase2/localMemory/__tests__/MemoryMarkdownParserTest.test.ts

echo "PASS: opfs_unavailable_corruption"
exit 0
