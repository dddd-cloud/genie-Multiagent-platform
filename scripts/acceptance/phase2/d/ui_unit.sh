#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}/ui"

echo "==> D ui_unit: phase2 unit tests"
pnpm test:phase2

echo "==> D ui_unit: full ui vitest (non-regression)"
pnpm test

echo "PASS: ui_unit"
exit 0
