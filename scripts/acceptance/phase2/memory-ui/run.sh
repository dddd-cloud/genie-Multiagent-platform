#!/usr/bin/env bash
# MVP-CONTRACT-005 R3 MEMORY-UI acceptance orchestrator.
# Fixed whitelist — no globs.
# Exit: any FAIL→1; no FAIL but BLOCKED→2; all PASS→0
set -u
set -o pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EVIDENCE_PY="${ROOT_DIR}/scripts/acceptance/evidence"

RUN_ID="${PHASE2_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)-$RANDOM}"
EVIDENCE_DIR="${EVIDENCE_DIR_OVERRIDE:-${ROOT_DIR}/results/phase2/${RUN_ID}}"
mkdir -p "${EVIDENCE_DIR}/memory-ui" "${EVIDENCE_DIR}/logs"

SUMMARY_JSONL="${EVIDENCE_DIR}/memory-ui/_summary.jsonl"
: >"${SUMMARY_JSONL}"

HAS_FAIL=0
HAS_BLOCKED=0

SCRIPTS=(
  ui_lint_typecheck_build.sh
  memory_existing_regression.sh
  memory_budget.sh
  orchestration_event_trace_v1_v2.sh
  snapshot_restore.sh
  pyodide_worker_smoke.sh
  pyodide_snapshot_no_replay.sh
  mock_e2e.sh
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
  echo "[memory-ui] ${name} => ${result} (exit=${exit_code}) ${message}"
}

echo "==> Phase2 MEMORY-UI acceptance (runId=${RUN_ID})"
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
  code=$?
  set -e
  if [[ ${code} -eq 0 ]]; then
    record_step "${script}" "PASS" 0
  elif [[ ${code} -eq 2 ]]; then
    HAS_BLOCKED=1
    record_step "${script}" "BLOCKED" 2
  else
    HAS_FAIL=1
    record_step "${script}" "FAIL" "${code}"
  fi
done

collect_evidence_best_effort

if [[ ${HAS_FAIL} -ne 0 ]]; then
  echo "FAIL: phase2-memory-ui-005-acceptance"
  exit 1
fi
if [[ ${HAS_BLOCKED} -ne 0 ]]; then
  echo "BLOCKED: phase2-memory-ui-005-acceptance"
  exit 2
fi
echo "PASS: phase2-memory-ui-005-acceptance"
exit 0
