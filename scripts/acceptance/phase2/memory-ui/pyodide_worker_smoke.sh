#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}/ui"
export VITE_PYODIDE_INDEX_URL="${VITE_PYODIDE_INDEX_URL:-https://cdn.jsdelivr.net/pyodide/v0.27.7/full/}"

echo "==> pyodide_worker_smoke"
pnpm exec vitest run \
  src/features/phase2/skillRuntime/__tests__/PyodideWorkerSmokeTest.test.ts \
  src/features/phase2/skillRuntime/__tests__/PyodideJsonInputOutputTest.test.ts \
  src/features/phase2/skillRuntime/__tests__/PyodidePurePythonPackageTest.test.ts \
  src/features/phase2/skillRuntime/__tests__/PyodideUnsupportedPackageTest.test.ts \
  src/features/phase2/skillRuntime/__tests__/PyodideBundleTraversalRejectTest.test.ts \
  src/features/phase2/skillRuntime/__tests__/PyodideTimeoutRecreateWorkerTest.test.ts \
  src/features/phase2/skillRuntime/__tests__/PyodideResultCallbackTest.test.ts \
  src/features/phase2/skillRuntime/__tests__/BrowserSkillLiveSignalTest.test.ts

echo "PASS: pyodide_worker_smoke"
exit 0
