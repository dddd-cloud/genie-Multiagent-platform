#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}/ui"
export SERVICE_BASE_URL="${SERVICE_BASE_URL:-http://127.0.0.1:8080}"
export VITE_PHASE2_ENABLED="${VITE_PHASE2_ENABLED:-true}"
export VITE_PYODIDE_INDEX_URL="${VITE_PYODIDE_INDEX_URL:-https://cdn.jsdelivr.net/pyodide/v0.27.7/full/}"

echo "==> memory-ui ui_lint_typecheck_build"
pnpm lint
pnpm typecheck
pnpm build
echo "PASS: ui_lint_typecheck_build"
exit 0
