#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}/ui"

echo "==> snapshot_restore"
pnpm exec vitest run \
  src/features/phase2/orchestration/__tests__/SnapshotOrchestrationHydrateTest.test.ts \
  src/features/phase2/orchestration/__tests__/SnapshotV2HydrateTest.test.ts \
  src/features/conversation/__tests__/hydrateConversation.test.ts

echo "PASS: snapshot_restore"
exit 0
