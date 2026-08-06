#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}/ui"

echo "==> D ui_mock_e2e: playwright phase2 mock"
pnpm e2e:phase2:mock

echo "PASS: ui_mock_e2e"
exit 0
