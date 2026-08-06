#!/usr/bin/env bash
# Phase2 full acceptance orchestrator.
# Whitelist order: contract → a → b → c → d
#
# real_combo: PHASE2_REAL_E2E_READY!=1 → BLOCKED (exit 2), never FAIL for unreadiness.
# When ready, runs d/real_e2e.sh (PASS=0 / FAIL=1 / BLOCKED=2).
set -u
set -o pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "${ROOT_DIR}"

EVIDENCE_PY="${ROOT_DIR}/scripts/acceptance/evidence"
RUN_ID="${PHASE2_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)-$RANDOM}"
EVIDENCE_DIR="${EVIDENCE_DIR_OVERRIDE:-${ROOT_DIR}/results/phase2/${RUN_ID}}"
mkdir -p "${EVIDENCE_DIR}/gates" "${EVIDENCE_DIR}/logs"

export PHASE2_RUN_ID="${RUN_ID}"
export EVIDENCE_DIR_OVERRIDE="${EVIDENCE_DIR}"

SUMMARY_JSONL="${EVIDENCE_DIR}/gates/_summary.jsonl"
: >"${SUMMARY_JSONL}"

HAS_FAIL=0
HAS_BLOCKED=0

GATES=(
  "contract:scripts/acceptance/phase2/contract/run.sh"
  "a:scripts/acceptance/phase2/a/run.sh"
  "b:scripts/acceptance/phase2/b/run.sh"
  "c:scripts/acceptance/phase2/c/run.sh"
  "d:scripts/acceptance/phase2/d/run.sh"
)

record_gate() {
  local gate="$1"
  local result="$2"
  local exit_code="$3"
  local message="${4:-}"
  if command -v python3 >/dev/null 2>&1; then
    python3 - "${SUMMARY_JSONL}" "${gate}" "${result}" "${exit_code}" "${message}" <<'PY'
import json, sys
path, gate, result, exit_code, message = sys.argv[1:6]
with open(path, "a", encoding="utf-8") as fh:
    fh.write(json.dumps({
        "gate": gate,
        "result": result,
        "exitCode": int(exit_code),
        "message": message,
    }, ensure_ascii=False) + "\n")
PY
  else
    echo "{\"gate\":\"${gate}\",\"result\":\"${result}\",\"exitCode\":${exit_code}}" >>"${SUMMARY_JSONL}"
  fi
  echo "[phase2] ${gate} => ${result} (exit=${exit_code}) ${message}"
}

echo "==> Phase2 acceptance run_all (runId=${RUN_ID})"
echo "Evidence: ${EVIDENCE_DIR}"

for entry in "${GATES[@]}"; do
  gate="${entry%%:*}"
  path="${entry#*:}"
  log_file="${EVIDENCE_DIR}/logs/${gate}.log"
  if [[ ! -f "${ROOT_DIR}/${path}" ]]; then
    HAS_FAIL=1
    record_gate "${gate}" "FAIL" 1 "missing ${path}"
    continue
  fi
  set +e
  bash "${ROOT_DIR}/${path}" >"${log_file}" 2>&1
  rc=$?
  set -e
  if [[ ${rc} -eq 0 ]]; then
    record_gate "${gate}" "PASS" 0
  elif [[ ${rc} -eq 2 ]]; then
    HAS_BLOCKED=1
    record_gate "${gate}" "BLOCKED" 2
  else
    HAS_FAIL=1
    record_gate "${gate}" "FAIL" "${rc}"
  fi
done

# 真实组合检查: unreadiness → BLOCKED; when ready, run real_e2e (never fake PASS).
echo "==> 真实组合检查"
if [[ ${HAS_BLOCKED} -ne 0 ]]; then
  record_gate "real_combo" "BLOCKED" 2 "prior gate BLOCKED — skip real combination"
elif [[ "${PHASE2_REAL_E2E_READY:-}" != "1" ]]; then
  HAS_BLOCKED=1
  record_gate "real_combo" "BLOCKED" 2 "PHASE2_REAL_E2E_READY!=1"
else
  set +e
  bash "${ROOT_DIR}/scripts/acceptance/phase2/d/real_e2e.sh" \
    >"${EVIDENCE_DIR}/logs/real_combo.log" 2>&1
  rc=$?
  set -e
  if [[ ${rc} -eq 0 ]]; then
    record_gate "real_combo" "PASS" 0
  elif [[ ${rc} -eq 2 ]]; then
    HAS_BLOCKED=1
    record_gate "real_combo" "BLOCKED" 2
  else
    HAS_FAIL=1
    record_gate "real_combo" "FAIL" "${rc}"
  fi
fi

if command -v python3 >/dev/null 2>&1; then
  python3 "${EVIDENCE_PY}/collect_git.py" --out "${EVIDENCE_DIR}/git.json" 2>/dev/null || true
  python3 "${EVIDENCE_PY}/collect_environment.py" --out "${EVIDENCE_DIR}/environment.json" 2>/dev/null || true
  python3 "${EVIDENCE_PY}/build_manifest.py" --evidence-dir "${EVIDENCE_DIR}" 2>/dev/null || true
fi

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
