#!/usr/bin/env bash
# Phase2-D acceptance orchestrator (Stage 2).
# Fixed whitelist order — no globs.
# real_e2e.sh: BLOCKED(2) when stack not ready; FAIL(1) when ready but tests fail.
# Exit: any FAIL→1; no FAIL but BLOCKED→2; all PASS→0
set -u
set -o pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EVIDENCE_PY="${ROOT_DIR}/scripts/acceptance/evidence"

RUN_ID="${PHASE2_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)-$RANDOM}"
EVIDENCE_DIR="${EVIDENCE_DIR_OVERRIDE:-${ROOT_DIR}/results/phase2/${RUN_ID}}"
mkdir -p "${EVIDENCE_DIR}/d" "${EVIDENCE_DIR}/logs"

SUMMARY_JSONL="${EVIDENCE_DIR}/d/_summary.jsonl"
: >"${SUMMARY_JSONL}"

HAS_FAIL=0
HAS_BLOCKED=0

SCRIPTS=(
  contract_validate.sh
  ui_lint_typecheck_build.sh
  ui_unit.sh
  ui_mock_e2e.sh
  local_memory_user_isolation.sh
  opfs_unavailable_corruption.sh
  management_pages.sh
  orchestration_timeline.sh
  snapshot_refresh_restore.sh
  credential_ui_no_persist.sh
  version_conflict_ui.sh
  real_e2e.sh
)

collect_evidence_best_effort() {
  if command -v python3 >/dev/null 2>&1; then
    python3 "${EVIDENCE_PY}/collect_git.py" --out "${EVIDENCE_DIR}/git.json" 2>/dev/null || true
    python3 "${EVIDENCE_PY}/collect_environment.py" --out "${EVIDENCE_DIR}/environment.json" 2>/dev/null || true
    python3 "${EVIDENCE_PY}/build_manifest.py" --evidence-dir "${EVIDENCE_DIR}" 2>/dev/null || true
  fi
}

record_step() {
  local name="$1"
  local result="$2"
  local exit_code="$3"
  local message="${4:-}"
  if command -v python3 >/dev/null 2>&1; then
    python3 - "${SUMMARY_JSONL}" "${name}" "${result}" "${exit_code}" "${message}" <<'PY'
import json, sys
path, name, result, exit_code, message = sys.argv[1:6]
with open(path, "a", encoding="utf-8") as fh:
    fh.write(json.dumps({
        "step": name,
        "result": result,
        "exitCode": int(exit_code),
        "message": message,
    }, ensure_ascii=False) + "\n")
PY
  else
    echo "{\"step\":\"${name}\",\"result\":\"${result}\",\"exitCode\":${exit_code}}" >>"${SUMMARY_JSONL}"
  fi
  echo "[d] ${name} => ${result} (exit=${exit_code}) ${message}"
}

echo "==> Phase2-D acceptance (runId=${RUN_ID})"
echo "Evidence: ${EVIDENCE_DIR}"

for script in "${SCRIPTS[@]}"; do
  path="${SCRIPT_DIR}/${script}"
  log_file="${EVIDENCE_DIR}/logs/${script}.log"
  if [[ ! -f "${path}" ]]; then
    HAS_FAIL=1
    record_step "${script}" "FAIL" 1 "missing script"
    continue
  fi
  set +e
  bash "${path}" >"${log_file}" 2>&1
  rc=$?
  set -e
  if [[ ${rc} -eq 0 ]]; then
    record_step "${script}" "PASS" 0
  elif [[ ${rc} -eq 2 ]]; then
    HAS_BLOCKED=1
    record_step "${script}" "BLOCKED" 2
  else
    HAS_FAIL=1
    record_step "${script}" "FAIL" "${rc}"
  fi
done

collect_evidence_best_effort

if [[ ${HAS_FAIL} -ne 0 ]]; then
  echo "Overall: FAIL"
  exit 1
fi
if [[ ${HAS_BLOCKED} -ne 0 ]]; then
  echo "Overall: BLOCKED"
  exit 2
fi
echo "Overall: PASS"
exit 0
