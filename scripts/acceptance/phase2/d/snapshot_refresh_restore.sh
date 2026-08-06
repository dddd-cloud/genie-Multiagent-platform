#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}/ui"

echo "==> D snapshot_refresh_restore"
pnpm vitest run \
  src/features/phase2/orchestration/__tests__/SnapshotOrchestrationHydrateTest.test.ts \
  src/features/conversation/__tests__/hydrateConversation.test.ts

echo "PASS: snapshot_refresh_restore"
exit 0
