#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}/ui"

echo "==> D credential_ui_no_persist"
pnpm vitest run src/features/phase2/mcp/__tests__/McpCredentialUiTest.test.tsx

echo "PASS: credential_ui_no_persist"
exit 0
