#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}/ui"

echo "==> mock_e2e"
pnpm e2e:phase2:mock

echo "PASS: mock_e2e"
exit 0
