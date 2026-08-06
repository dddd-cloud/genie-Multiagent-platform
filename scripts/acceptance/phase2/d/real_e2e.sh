#!/usr/bin/env bash
# Stage 2: real Phase2 E2E against external A/B/C + UI stack.
# Never fake PASS with mock.
#
# Exit codes:
#   0 PASS  — real Playwright E2E passed
#   1 FAIL  — stack ready but tests failed
#   2 BLOCKED — A/B/C missing, PHASE2_REAL_E2E_READY!=1, or health probe failed
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}"

blocked() {
  echo "BLOCKED: $1"
  echo "Overall: BLOCKED"
  exit 2
}

A_RUN="${ROOT_DIR}/scripts/acceptance/phase2/a/run.sh"
B_RUN="${ROOT_DIR}/scripts/acceptance/phase2/b/run.sh"
C_RUN="${ROOT_DIR}/scripts/acceptance/phase2/c/run.sh"

# Require A/B/C run.sh exist (real modules merged).
# Do NOT treat A's "Overall: BLOCKED" string as a Stage-1 stub — that text is
# used for tooling failures in the real module as well.
if [[ ! -f "${A_RUN}" ]] || [[ ! -f "${B_RUN}" ]] || [[ ! -f "${C_RUN}" ]]; then
  blocked "Phase2 A/B/C acceptance scripts missing"
fi

if [[ "${PHASE2_REAL_E2E_READY:-}" != "1" ]]; then
  blocked "PHASE2_REAL_E2E_READY!=1 (real stack not ready)"
fi

# Health: UI must be up. Backend via UI /web proxy is optional but BLOCKED on fail
# (not FAIL) so a half-started stack does not look like a test regression.
if ! curl -fsS "http://127.0.0.1:3000" >/dev/null 2>&1; then
  blocked "UI not healthy at http://127.0.0.1:3000"
fi

if ! curl -fsS "http://127.0.0.1:3000/web/health" >/dev/null 2>&1 \
  && ! curl -fsS "http://127.0.0.1:8080/web/health" >/dev/null 2>&1; then
  blocked "backend /web/health not reachable via UI proxy :3000 or :8080"
fi

echo "==> D real_e2e: playwright phase2 real"
(
  cd ui
  pnpm e2e:phase2:real
)

echo "PASS: real_e2e"
exit 0
