#!/usr/bin/env bash
# JoyAgent MVP acceptance orchestrator (D ownership).
# Continues after gate failures; overall PASS only when every gate PASSes.
# Intended for Linux / Git Bash / CI (not native PowerShell).

# Do NOT use `set -e` for the whole script — each gate captures its own exit code.
set -u
set -o pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

EVIDENCE_PY="$ROOT_DIR/scripts/acceptance/evidence"
COMPOSE_FILE="$ROOT_DIR/deploy/docker-compose.mvp.yml"
COMPOSE=(docker compose -f "$COMPOSE_FILE")

RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)-$RANDOM"
EVIDENCE_DIR="${EVIDENCE_DIR_OVERRIDE:-$ROOT_DIR/evidence/mvp/$RUN_ID}"
OVERALL_PASS=1

mkdir -p \
  "$EVIDENCE_DIR/a_results" \
  "$EVIDENCE_DIR/b_results" \
  "$EVIDENCE_DIR/c_results" \
  "$EVIDENCE_DIR/ui_test_report" \
  "$EVIDENCE_DIR/playwright_report" \
  "$EVIDENCE_DIR/logs" \
  "$EVIDENCE_DIR/gates"

SUMMARY_JSONL="$EVIDENCE_DIR/gates/_summary_gates.jsonl"
: >"$SUMMARY_JSONL"

export EVIDENCE_DIR
export MVP_RUN_ID="$RUN_ID"

iso_now() {
  date -u +%Y-%m-%dT%H:%M:%SZ
}

write_gate_json() {
  local out_path="$1"
  local gate="$2"
  local command="$3"
  local exit_code="$4"
  local started="$5"
  local finished="$6"
  local result="$7"
  local message="${8:-}"
  python3 - "$out_path" "$gate" "$command" "$exit_code" "$started" "$finished" "$result" "$message" <<'PY'
import json, sys
out, gate, command, exit_code, started, finished, result, message = sys.argv[1:9]
payload = {
    "gate": gate,
    "command": command,
    "exitCode": int(exit_code),
    "startedAt": started,
    "finishedAt": finished,
    "result": result,
    "message": message,
}
with open(out, "w", encoding="utf-8") as fh:
    json.dump(payload, fh, indent=2, ensure_ascii=False)
    fh.write("\n")
PY
}

record_gate() {
  local gate="$1"
  local result="$2"
  local exit_code="$3"
  local message="${4:-}"
  python3 - "$SUMMARY_JSONL" "$gate" "$result" "$exit_code" "$message" <<'PY'
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
  if [[ "$result" != "PASS" ]]; then
    OVERALL_PASS=0
  fi
  echo "[gate] $gate => $result (exit=$exit_code) ${message}"
}

write_acceptance_summary() {
  local final_status="$1"
  python3 - "$EVIDENCE_DIR/acceptance_summary.json" "$RUN_ID" "$final_status" "$SUMMARY_JSONL" <<'PY'
import json, sys
from pathlib import Path
out = Path(sys.argv[1])
run_id = sys.argv[2]
final_status = sys.argv[3]
jsonl = Path(sys.argv[4])
gates = []
if jsonl.is_file():
    for line in jsonl.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        gates.append(json.loads(line))
payload = {
    "runId": run_id,
    "finalStatus": final_status,
    "gateCount": len(gates),
    "passCount": sum(1 for g in gates if g.get("result") == "PASS"),
    "failCount": sum(1 for g in gates if g.get("result") == "FAIL"),
    "blockedCount": sum(1 for g in gates if g.get("result") == "BLOCKED"),
    "gates": gates,
}
out.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
PY
}

run_capture() {
  # Usage: run_capture <gate> <out_json> <stdout_log> <stderr_log> -- <command...>
  local gate="$1"
  local out_json="$2"
  local stdout_log="$3"
  local stderr_log="$4"
  shift 4
  if [[ "${1:-}" == "--" ]]; then
    shift
  fi
  local started finished exit_code result
  started="$(iso_now)"
  "$@" >"$stdout_log" 2>"$stderr_log"
  exit_code=$?
  finished="$(iso_now)"
  if [[ $exit_code -eq 0 ]]; then
    result="PASS"
  else
    result="FAIL"
  fi
  local cmd_str="$*"
  write_gate_json "$out_json" "$gate" "$cmd_str" "$exit_code" "$started" "$finished" "$result" ""
  if [[ -f "$EVIDENCE_PY/validate_result.py" ]]; then
    python3 "$EVIDENCE_PY/validate_result.py" "$out_json" >/dev/null 2>&1 || true
  fi
  record_gate "$gate" "$result" "$exit_code" ""
  return 0
}

write_blocked() {
  local gate="$1"
  local out_json="$2"
  local message="$3"
  local started finished
  started="$(iso_now)"
  finished="$started"
  write_gate_json "$out_json" "$gate" "(missing)" 2 "$started" "$finished" "BLOCKED" "$message"
  record_gate "$gate" "BLOCKED" 2 "$message"
}

run_owner_scripts() {
  local owner="$1"   # a|b|c
  local dest_dir="$EVIDENCE_DIR/${owner}_results"
  local script_dir="$ROOT_DIR/scripts/acceptance/$owner"
  local listed
  case "$owner" in
    a)
      listed=(
        mysql_startup.sh
        flyway_validate.sh
        auth_session.sh
        csrf_security.sh
        internal_token.sh
        user_admin.sh
      )
      ;;
    b)
      listed=(
        conversation_crud.sh
        conversation_isolation.sh
        duplicate_request.sh
        conversation_busy.sh
        message_state_machine.sh
        stale_message_recovery.sh
        history_context.sh
      )
      ;;
    c)
      listed=(
        fake_agent_success.sh
        fake_agent_500.sh
        fake_agent_disconnect.sh
        fake_agent_malformed.sh
        fake_agent_no_final.sh
        snapshot_restore.sh
        snapshot_too_large.sh
        history_context.sh
        react_plan_regression.sh
      )
      ;;
    *)
      echo "unknown owner: $owner" >&2
      return 0
      ;;
  esac

  mkdir -p "$dest_dir"
  local script name gate out_json stdout_log stderr_log started finished exit_code result
  for name in "${listed[@]}"; do
    script="$script_dir/$name"
    gate="${owner}_${name%.sh}"
    out_json="$dest_dir/${name%.sh}.json"
    stdout_log="$dest_dir/${name%.sh}.stdout.log"
    stderr_log="$dest_dir/${name%.sh}.stderr.log"
    if [[ ! -f "$script" ]]; then
      local owner_upper
      owner_upper="$(printf '%s' "$owner" | tr 'a-z' 'A-Z')"
      write_blocked "$gate" "$out_json" \
        "BLOCKED: $script missing — ${owner_upper} owner must provide acceptance scripts (D will not invent PASS)."
      continue
    fi
    started="$(iso_now)"
    bash "$script" >"$stdout_log" 2>"$stderr_log"
    exit_code=$?
    finished="$(iso_now)"
    if [[ $exit_code -eq 0 ]]; then
      result="PASS"
    else
      result="FAIL"
    fi
    # Prefer module JSON if the script wrote one beside the log, else synthesize.
    if [[ -f "$dest_dir/${name%.sh}.result.json" ]]; then
      cp "$dest_dir/${name%.sh}.result.json" "$out_json" 2>/dev/null || true
      python3 "$EVIDENCE_PY/validate_result.py" "$out_json" >/dev/null 2>&1 || \
        write_gate_json "$out_json" "$gate" "bash $script" "$exit_code" "$started" "$finished" "$result" \
          "module JSON present but failed validation; using synthesized result"
    else
      write_gate_json "$out_json" "$gate" "bash $script" "$exit_code" "$started" "$finished" "$result" ""
    fi
    record_gate "$gate" "$result" "$exit_code" ""
  done
}

wait_compose_healthy() {
  local service="$1"
  local attempts="${2:-60}"
  local i cid health
  for ((i = 1; i <= attempts; i++)); do
    cid="$("${COMPOSE[@]}" ps -q "$service" 2>/dev/null || true)"
    if [[ -n "$cid" ]]; then
      health="$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}running-no-healthcheck{{end}}' "$cid" 2>/dev/null || echo unknown)"
      if [[ "$health" == "healthy" ]]; then
        return 0
      fi
      # Services without a healthcheck are ready once running.
      if [[ "$health" == "running-no-healthcheck" ]]; then
        local state
        state="$(docker inspect --format='{{.State.Status}}' "$cid" 2>/dev/null || echo unknown)"
        if [[ "$state" == "running" ]]; then
          return 0
        fi
      fi
    fi
    sleep 5
  done
  return 1
}

recreate_backend_with_mode() {
  local mode="$1"
  export MVP_FAKE_AGENT_MODE="$mode"
  # Plan §14.9: tool/client share backend netns — recreate all three together.
  "${COMPOSE[@]}" up -d --force-recreate genie-backend genie-tool genie-client
  wait_compose_healthy genie-backend 72 || return 1
  wait_loopback_ready 1601 /openapi.json 72 || return 1
  wait_loopback_ready 8188 /health 72 || return 1
}

wait_loopback_ready() {
  local port="$1"
  local path="$2"
  local attempts="${3:-72}"
  local i
  for ((i = 1; i <= attempts; i++)); do
    if "${COMPOSE[@]}" exec -T genie-backend bash -c \
      "exec 3<>/dev/tcp/127.0.0.1/${port}; printf 'GET ${path} HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n' >&3; timeout 5 cat <&3 | head -n1 | grep -Eqi '200'" \
      >/dev/null 2>&1; then
      return 0
    fi
    sleep 5
  done
  echo "loopback ready check failed: 127.0.0.1:${port}${path}" >&2
  return 1
}

echo "=== MVP acceptance runId=$RUN_ID ==="
echo "EVIDENCE_DIR=$EVIDENCE_DIR"

# ---------------------------------------------------------------------------
# 1) Environment
# ---------------------------------------------------------------------------
run_capture "collect_environment" \
  "$EVIDENCE_DIR/gates/collect_environment.json" \
  "$EVIDENCE_DIR/logs/collect_environment.stdout.log" \
  "$EVIDENCE_DIR/logs/collect_environment.stderr.log" \
  -- python3 "$EVIDENCE_PY/collect_environment.py" --out "$EVIDENCE_DIR/environment.json"

# ---------------------------------------------------------------------------
# 2) Git provenance
# ---------------------------------------------------------------------------
run_capture "collect_git" \
  "$EVIDENCE_DIR/gates/collect_git.json" \
  "$EVIDENCE_DIR/logs/collect_git.stdout.log" \
  "$EVIDENCE_DIR/logs/collect_git.stderr.log" \
  -- python3 "$EVIDENCE_PY/collect_git.py" --out "$EVIDENCE_DIR/git_provenance.json"

# ---------------------------------------------------------------------------
# 3) Contract validate (ui)
# ---------------------------------------------------------------------------
run_capture "contract_validate" \
  "$EVIDENCE_DIR/contract_validation.json" \
  "$EVIDENCE_DIR/logs/contract_validate.stdout.log" \
  "$EVIDENCE_DIR/logs/contract_validate.stderr.log" \
  -- bash -lc 'cd ui && pnpm contract:validate'

# ---------------------------------------------------------------------------
# 4–6) A / B / C scripts
# ---------------------------------------------------------------------------
run_owner_scripts a
run_owner_scripts b
run_owner_scripts c

# ---------------------------------------------------------------------------
# 7) mvp-ui (pnpm test/build chain)
# ---------------------------------------------------------------------------
run_capture "mvp_ui" \
  "$EVIDENCE_DIR/gates/mvp_ui.json" \
  "$EVIDENCE_DIR/ui_test_report/mvp_ui.stdout.log" \
  "$EVIDENCE_DIR/ui_test_report/mvp_ui.stderr.log" \
  -- bash -lc 'cd ui && pnpm contract:validate && pnpm install --frozen-lockfile && pnpm lint && pnpm typecheck && pnpm test && pnpm build'

# ---------------------------------------------------------------------------
# 8) Compose up + wait healthy
# ---------------------------------------------------------------------------
COMPOSE_STARTED=0
started="$(iso_now)"
"${COMPOSE[@]}" up -d --build >"$EVIDENCE_DIR/logs/compose_up.stdout.log" 2>"$EVIDENCE_DIR/logs/compose_up.stderr.log"
compose_ec=$?
if [[ $compose_ec -eq 0 ]]; then
  wait_compose_healthy mysql 36
  mysql_ec=$?
  wait_compose_healthy genie-backend 72
  backend_ec=$?
  # Plan §14.8: tool/client ready from backend netns before UI/Playwright.
  wait_loopback_ready 1601 /openapi.json 72
  tool_ec=$?
  wait_loopback_ready 8188 /health 72
  client_ec=$?
  # ui has no healthcheck — brief settle after backend+tools ready
  sleep 8
  if [[ $mysql_ec -eq 0 && $backend_ec -eq 0 && $tool_ec -eq 0 && $client_ec -eq 0 ]]; then
    compose_ec=0
    COMPOSE_STARTED=1
  else
    compose_ec=1
  fi
fi
finished="$(iso_now)"
if [[ $compose_ec -eq 0 ]]; then
  compose_result="PASS"
else
  compose_result="FAIL"
fi
write_gate_json "$EVIDENCE_DIR/gates/compose_up.json" "compose_up" \
  "docker compose -f deploy/docker-compose.mvp.yml up -d --build" \
  "$compose_ec" "$started" "$finished" "$compose_result" ""
record_gate "compose_up" "$compose_result" "$compose_ec" ""

# ---------------------------------------------------------------------------
# 9) Playwright SUCCESS group (happy-path specs; failure/restart/concurrency are separate gates)
# Plan §15: real E2E must not skip via unset MVP_E2E_READY (skipped-all ⇒ false PASS).
# ---------------------------------------------------------------------------
export MVP_E2E_READY=1
if [[ $COMPOSE_STARTED -eq 1 ]]; then
  run_capture "playwright_success" \
    "$EVIDENCE_DIR/gates/playwright_success.json" \
    "$EVIDENCE_DIR/playwright_report/success.stdout.log" \
    "$EVIDENCE_DIR/playwright_report/success.stderr.log" \
    -- bash -lc "cd ui && MVP_E2E_READY=1 pnpm exec playwright test \
      e2e/auth.spec.ts \
      e2e/conversation-crud.spec.ts \
      e2e/react-history-refresh.spec.ts \
      e2e/plan-history-refresh.spec.ts \
      e2e/isolation.spec.ts \
      --reporter=list \
      --output='$EVIDENCE_DIR/playwright_report/success-artifacts'"
else
  write_gate_json "$EVIDENCE_DIR/gates/playwright_success.json" "playwright_success" \
    "(skipped — compose unhealthy)" 1 "$(iso_now)" "$(iso_now)" "FAIL" \
    "Compose stack not healthy; Playwright SUCCESS group not run."
  record_gate "playwright_success" "FAIL" 1 "compose unhealthy"
fi

# ---------------------------------------------------------------------------
# 10) Fake failure modes — recreate backend per mode + stream-failure e2e / C hooks
# ---------------------------------------------------------------------------
FAKE_MODES=(
  HTTP_500
  DISCONNECT_AFTER_N_EVENTS
  MALFORMED_EVENT
  NO_FINAL_EVENT
  SNAPSHOT_TOO_LARGE
)
for mode in "${FAKE_MODES[@]}"; do
  gate="fake_mode_${mode}"
  out_json="$EVIDENCE_DIR/gates/${gate}.json"
  stdout_log="$EVIDENCE_DIR/logs/${gate}.stdout.log"
  stderr_log="$EVIDENCE_DIR/logs/${gate}.stderr.log"
  started="$(iso_now)"
  exit_code=1
  if [[ $COMPOSE_STARTED -ne 1 ]]; then
    write_gate_json "$out_json" "$gate" "MVP_FAKE_AGENT_MODE=$mode" 1 "$started" "$(iso_now)" "FAIL" \
      "Compose not healthy; cannot exercise fake mode."
    record_gate "$gate" "FAIL" 1 "compose unhealthy"
    continue
  fi
  {
    echo "Recreating backend with MVP_FAKE_AGENT_MODE=$mode"
    if ! recreate_backend_with_mode "$mode"; then
      echo "backend recreate failed for mode=$mode"
      exit_code=1
    else
      (
        cd ui
        MVP_E2E_READY=1 MVP_FAKE_AGENT_MODE="$mode" pnpm exec playwright test e2e/stream-failure.spec.ts --reporter=list \
          --output="$EVIDENCE_DIR/playwright_report/fake-${mode}-artifacts"
      )
      exit_code=$?
    fi
  } >"$stdout_log" 2>"$stderr_log"
  finished="$(iso_now)"
  if [[ $exit_code -eq 0 ]]; then
    result="PASS"
  else
    result="FAIL"
  fi
  write_gate_json "$out_json" "$gate" "MVP_FAKE_AGENT_MODE=$mode + playwright stream-failure" \
    "$exit_code" "$started" "$finished" "$result" ""
  record_gate "$gate" "$result" "$exit_code" ""
done

# Restore default SUCCESS mode for remaining gates
if [[ $COMPOSE_STARTED -eq 1 ]]; then
  recreate_backend_with_mode SUCCESS >"$EVIDENCE_DIR/logs/restore_success_mode.log" 2>&1 || true
fi

# ---------------------------------------------------------------------------
# 11) Service restart test — plan §15.4 requires SLOW_STREAM then docker restart
# ---------------------------------------------------------------------------
if [[ $COMPOSE_STARTED -eq 1 ]]; then
  recreate_backend_with_mode SLOW_STREAM >"$EVIDENCE_DIR/logs/service_restart_slow_stream.log" 2>&1 || true
  run_capture "service_restart" \
    "$EVIDENCE_DIR/gates/service_restart.json" \
    "$EVIDENCE_DIR/playwright_report/service_restart.stdout.log" \
    "$EVIDENCE_DIR/playwright_report/service_restart.stderr.log" \
    -- bash -lc 'cd ui && MVP_E2E_READY=1 MVP_FAKE_AGENT_MODE=SLOW_STREAM pnpm exec playwright test e2e/service-restart.spec.ts --reporter=list'
  recreate_backend_with_mode SUCCESS >"$EVIDENCE_DIR/logs/restore_success_after_restart.log" 2>&1 || true
else
  write_gate_json "$EVIDENCE_DIR/gates/service_restart.json" "service_restart" \
    "(skipped — compose unhealthy)" 1 "$(iso_now)" "$(iso_now)" "FAIL" \
    "Compose stack not healthy."
  record_gate "service_restart" "FAIL" 1 "compose unhealthy"
fi

# ---------------------------------------------------------------------------
# 12) Concurrency
# ---------------------------------------------------------------------------
if [[ $COMPOSE_STARTED -eq 1 ]]; then
  run_capture "concurrency" \
    "$EVIDENCE_DIR/gates/concurrency.json" \
    "$EVIDENCE_DIR/playwright_report/concurrency.stdout.log" \
    "$EVIDENCE_DIR/playwright_report/concurrency.stderr.log" \
    -- bash -lc 'cd ui && MVP_E2E_READY=1 pnpm exec playwright test e2e/concurrency.spec.ts --reporter=list'
else
  write_gate_json "$EVIDENCE_DIR/gates/concurrency.json" "concurrency" \
    "(skipped — compose unhealthy)" 1 "$(iso_now)" "$(iso_now)" "FAIL" \
    "Compose stack not healthy."
  record_gate "concurrency" "FAIL" 1 "compose unhealthy"
fi

# ---------------------------------------------------------------------------
# 13) Real smoke (local profile) — FAIL with reason if config missing
# ---------------------------------------------------------------------------
REGRESSION_JSON="$EVIDENCE_DIR/regression_results.json"
started="$(iso_now)"
REAL_SMOKE_REASON=""
REAL_SMOKE_RESULT="FAIL"
REAL_SMOKE_EC=1
REAL_SMOKE_CMD="real_smoke_local"

# Config: require explicit enable + key material outside the repo.
if [[ "${MVP_REAL_SMOKE_ENABLED:-}" != "1" ]]; then
  REAL_SMOKE_REASON="MVP_REAL_SMOKE_ENABLED is not set to 1; real smoke (local profile) config missing. Formal acceptance cannot treat Fake as real regression."
elif [[ -z "${MVP_REAL_LLM_API_KEY:-}${OPENAI_API_KEY:-}${LLM_API_KEY:-}" ]]; then
  REAL_SMOKE_REASON="Real LLM API key env missing (MVP_REAL_LLM_API_KEY / OPENAI_API_KEY / LLM_API_KEY)."
elif [[ ! -f "$ROOT_DIR/scripts/acceptance/c/react_plan_regression.sh" ]]; then
  REAL_SMOKE_REASON="scripts/acceptance/c/react_plan_regression.sh missing — C owner must provide real smoke script; config alone is insufficient."
else
  REAL_SMOKE_CMD="SPRING_PROFILES_ACTIVE=local bash scripts/acceptance/c/react_plan_regression.sh"
  SPRING_PROFILES_ACTIVE=local \
    bash "$ROOT_DIR/scripts/acceptance/c/react_plan_regression.sh" \
    >"$EVIDENCE_DIR/logs/real_smoke.stdout.log" 2>"$EVIDENCE_DIR/logs/real_smoke.stderr.log"
  REAL_SMOKE_EC=$?
  if [[ $REAL_SMOKE_EC -eq 0 ]]; then
    REAL_SMOKE_RESULT="PASS"
    REAL_SMOKE_REASON="Real smoke completed successfully."
  else
    REAL_SMOKE_REASON="Real smoke script exited non-zero."
  fi
fi
finished="$(iso_now)"
python3 - "$REGRESSION_JSON" "$REAL_SMOKE_RESULT" "$REAL_SMOKE_EC" "$REAL_SMOKE_REASON" "$started" "$finished" "$REAL_SMOKE_CMD" <<'PY'
import json, sys
path, result, ec, reason, started, finished, cmd = sys.argv[1:8]
payload = {
    "gate": "real_smoke_local",
    "command": cmd,
    "exitCode": int(ec),
    "startedAt": started,
    "finishedAt": finished,
    "result": result,
    "message": reason,
    "profile": "local",
    "checks": [
        "ReAct x1",
        "Plan x1",
        "search_or_report x1",
        "DataAgent allModels/preview x1",
    ],
}
with open(path, "w", encoding="utf-8") as fh:
    json.dump(payload, fh, indent=2, ensure_ascii=False)
    fh.write("\n")
PY
write_gate_json "$EVIDENCE_DIR/gates/real_smoke_local.json" "real_smoke_local" \
  "$REAL_SMOKE_CMD" "$REAL_SMOKE_EC" "$started" "$finished" "$REAL_SMOKE_RESULT" "$REAL_SMOKE_REASON"
record_gate "real_smoke_local" "$REAL_SMOKE_RESULT" "$REAL_SMOKE_EC" "$REAL_SMOKE_REASON"

# ---------------------------------------------------------------------------
# 14) Secret scan
# ---------------------------------------------------------------------------
run_capture "secret_scan" \
  "$EVIDENCE_DIR/gates/secret_scan.json" \
  "$EVIDENCE_DIR/logs/secret_scan.stdout.log" \
  "$EVIDENCE_DIR/logs/secret_scan.stderr.log" \
  -- python3 "$EVIDENCE_PY/secret_scan.py" --evidence-dir "$EVIDENCE_DIR" --out "$EVIDENCE_DIR/secret_scan.json"

# Mirror thin security_scan.json for Evidence tree completeness (D owns secret scan).
python3 - "$EVIDENCE_DIR/security_scan.json" "$EVIDENCE_DIR/secret_scan.json" <<'PY'
import json, sys
from pathlib import Path
out, secret_path = Path(sys.argv[1]), Path(sys.argv[2])
secret = {}
if secret_path.is_file():
    secret = json.loads(secret_path.read_text(encoding="utf-8"))
payload = {
    "result": secret.get("result", "FAIL"),
    "delegatedTo": "secret_scan.json",
    "findingCount": secret.get("findingCount", -1),
}
out.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
PY

# ---------------------------------------------------------------------------
# 15) Build + verify manifest
# ---------------------------------------------------------------------------
# Write acceptance_summary before manifest so it is hashed; then verify.
FINAL_STATUS="NOT PASS"
if [[ $OVERALL_PASS -eq 1 ]]; then
  FINAL_STATUS="PASS"
fi

write_acceptance_summary "$FINAL_STATUS"

run_capture "build_manifest" \
  "$EVIDENCE_DIR/gates/build_manifest.json" \
  "$EVIDENCE_DIR/logs/build_manifest.stdout.log" \
  "$EVIDENCE_DIR/logs/build_manifest.stderr.log" \
  -- python3 "$EVIDENCE_PY/build_manifest.py" --evidence-dir "$EVIDENCE_DIR"

run_capture "verify_manifest" \
  "$EVIDENCE_DIR/gates/verify_manifest.json" \
  "$EVIDENCE_DIR/logs/verify_manifest.stdout.log" \
  "$EVIDENCE_DIR/logs/verify_manifest.stderr.log" \
  -- python3 "$EVIDENCE_PY/verify_manifest.py" --evidence-dir "$EVIDENCE_DIR"

# Recompute final status including late gates; rebuild summary + remanifest once.
FINAL_STATUS="NOT PASS"
if [[ $OVERALL_PASS -eq 1 ]]; then
  FINAL_STATUS="PASS"
fi

write_acceptance_summary "$FINAL_STATUS"

# Final manifest must include updated acceptance_summary; verify again (not recorded as separate gates).
# Note: verifying after writing the manifest means the last build_manifest excludes only itself;
# any log files written during verify are reconciled by one rebuild if verify fails on unexpected files.
python3 "$EVIDENCE_PY/build_manifest.py" --evidence-dir "$EVIDENCE_DIR" \
  >"$EVIDENCE_DIR/logs/build_manifest_final.stdout.log" 2>"$EVIDENCE_DIR/logs/build_manifest_final.stderr.log"
python3 "$EVIDENCE_PY/verify_manifest.py" --evidence-dir "$EVIDENCE_DIR" \
  >"$EVIDENCE_DIR/logs/verify_manifest_final.stdout.log" 2>"$EVIDENCE_DIR/logs/verify_manifest_final.stderr.log"
final_verify_ec=$?
if [[ $final_verify_ec -ne 0 ]]; then
  # Rebuild once so verify logs themselves are included, then verify again.
  python3 "$EVIDENCE_PY/build_manifest.py" --evidence-dir "$EVIDENCE_DIR" >/dev/null 2>&1 || true
  if ! python3 "$EVIDENCE_PY/verify_manifest.py" --evidence-dir "$EVIDENCE_DIR" >/dev/null 2>&1; then
    OVERALL_PASS=0
    FINAL_STATUS="NOT PASS"
    record_gate "verify_manifest_final" "FAIL" 1 "Final manifest verification failed after summary update."
    write_acceptance_summary "NOT PASS"
    python3 "$EVIDENCE_PY/build_manifest.py" --evidence-dir "$EVIDENCE_DIR" >/dev/null 2>&1 || true
  fi
fi

echo "=== acceptance_summary: $FINAL_STATUS (runId=$RUN_ID) ==="
echo "Evidence: $EVIDENCE_DIR"

if [[ "$FINAL_STATUS" == "PASS" ]]; then
  exit 0
fi
exit 1
