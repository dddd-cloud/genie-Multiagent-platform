#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}/ui"

echo "==> pyodide_snapshot_no_replay"
pnpm exec vitest run \
  src/features/phase2/skillRuntime/__tests__/SnapshotSkillSignalDoesNotExecuteTest.test.ts

echo "PASS: pyodide_snapshot_no_replay"
exit 0
