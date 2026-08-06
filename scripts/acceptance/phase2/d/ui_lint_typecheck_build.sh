#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}/ui"

export SERVICE_BASE_URL="${SERVICE_BASE_URL:-http://127.0.0.1:8080}"
export VITE_PHASE2_ENABLED="${VITE_PHASE2_ENABLED:-true}"

echo "==> D ui_lint_typecheck_build: lint"
pnpm lint

echo "==> D ui_lint_typecheck_build: typecheck"
pnpm typecheck

echo "==> D ui_lint_typecheck_build: build"
pnpm build

echo "PASS: ui_lint_typecheck_build"
exit 0
