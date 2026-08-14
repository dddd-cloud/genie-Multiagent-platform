#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}/ui"

echo "==> orchestration_event_trace_v1_v2"
pnpm exec vitest run \
  src/features/phase2/orchestration/__tests__/EventV1CompatibilityTest.test.ts \
  src/features/phase2/orchestration/__tests__/EventV2ParallelReducerTest.test.ts \
  src/features/phase2/orchestration/__tests__/SameAgentTwoSubTasksTest.test.ts \
  src/features/phase2/orchestration/__tests__/RetryFallbackDegradedUiTest.test.ts \
  src/features/phase2/orchestration/__tests__/TraceV2SubTaskTest.test.ts \
  src/features/phase2/orchestration/__tests__/OrchestrationReducerTest.test.ts \
  src/features/phase2/orchestration/__tests__/OrchestrationTraceReducerTest.test.ts

echo "PASS: orchestration_event_trace_v1_v2"
exit 0
