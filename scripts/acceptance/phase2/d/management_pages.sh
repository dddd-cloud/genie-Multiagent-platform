#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}/ui"

echo "==> D management_pages: vitest agents/skills/mcp + version conflict"
pnpm vitest run \
  src/features/phase2/agents/__tests__/AgentEditorTest.test.tsx \
  src/features/phase2/skills/__tests__/SkillOrderingTest.test.tsx \
  src/features/phase2/mcp/__tests__/McpCredentialUiTest.test.tsx \
  src/features/phase2/__tests__/VersionConflictUiTest.test.tsx

echo "PASS: management_pages"
exit 0
