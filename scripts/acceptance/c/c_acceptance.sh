#!/usr/bin/env bash

scenario_gate_ids() {
  case "$1" in
    fake_agent_success) printf '%s' '["C-G2","C-G3","C-G5","C-G7"]' ;;
    fake_agent_500|fake_agent_disconnect|fake_agent_malformed|fake_agent_no_final) printf '%s' '["C-G3","C-G4","C-G7"]' ;;
    snapshot_restore) printf '%s' '["C-G5"]' ;;
    snapshot_too_large) printf '%s' '["C-G3","C-G5","C-G7"]' ;;
    history_context) printf '%s' '["C-G6"]' ;;
    react_plan_regression) printf '%s' '["C-G6","C-G9"]' ;;
    client_disconnect) printf '%s' '["C-G3","C-G8"]' ;;
    *) printf '%s' '[]' ;;
  esac
}

run_c_acceptance() {
  local scenario="$1"
  local script_path="$2"
  local script_dir
  local python_bin=""
  local started_at
  local finished_at
  local output
  local exit_code
  local gate_ids
  local runner_args=()

  shift 2
  runner_args=("$@")
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  gate_ids="$(scenario_gate_ids "$scenario")"
  for candidate in python3 python; do
    if [[ -n "$candidate" ]] && command -v "$candidate" >/dev/null 2>&1 && "$candidate" -c 'import sys; raise SystemExit(0 if sys.version_info >= (3, 9) else 1)' >/dev/null 2>&1; then
      python_bin="$candidate"
      break
    fi
  done

  if [[ -z "$python_bin" ]]; then
    finished_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf '{"command":"%s","exitCode":1,"startedAt":"%s","finishedAt":"%s","result":{"status":"BLOCKED","scenario":"%s","gateIds":%s,"reason":"PREREQUISITE_UNAVAILABLE","message":"Python 3.9 or newer is required","checks":[],"details":{}}}\n' "$(basename "$script_path")" "$started_at" "$finished_at" "$scenario" "$gate_ids"
    return 1
  fi

  set +e
  output="$("$python_bin" "$script_dir/c_acceptance.py" "$scenario" "$script_path" "${runner_args[@]}" 2>/dev/null)"
  exit_code=$?
  set -e
  if [[ "$output" != \{* ]]; then
    finished_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf '{"command":"%s","exitCode":1,"startedAt":"%s","finishedAt":"%s","result":{"status":"FAIL","scenario":"%s","gateIds":%s,"reason":"RUNNER_INVALID_OUTPUT","message":"acceptance runner did not produce JSON","checks":[],"details":{}}}\n' "$(basename "$script_path")" "$started_at" "$finished_at" "$scenario" "$gate_ids"
    return 1
  fi
  printf '%s\n' "$output"
  return "$exit_code"
}
