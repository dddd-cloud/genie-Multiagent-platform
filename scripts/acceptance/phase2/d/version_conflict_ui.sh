#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}/ui"

echo "==> D version_conflict_ui"
pnpm vitest run src/features/phase2/__tests__/VersionConflictUiTest.test.tsx

echo "PASS: version_conflict_ui"
exit 0
