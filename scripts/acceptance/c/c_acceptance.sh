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

scenario_test_selector() {
  case "$1" in
    fake_agent_success)
      printf '%s' 'MultiAgentServiceSuccessTest,GptProcessServiceOrchestrationTest,FakeAgentAcceptanceFilterTest'
      ;;
    fake_agent_500)
      printf '%s' 'MultiAgentServiceFailureTest#nonSuccessHttpStatusUsesDownstreamError'
      ;;
    fake_agent_disconnect)
      printf '%s' 'MultiAgentServiceFailureTest#readFailureAfterStreamEstablishmentUsesStreamInterrupted,MultiAgentServiceCancellationTest'
      ;;
    fake_agent_no_final)
      printf '%s' 'MultiAgentServiceFailureTest#eofAndDoneWithoutSuccessfulFinalEventUseNoFinalEvent'
      ;;
    fake_agent_malformed)
      printf '%s' 'MultiAgentServiceFailureTest#malformedJsonUsesStreamInterrupted'
      ;;
    snapshot_restore)
      printf '%s' 'StreamSnapshotBufferTest,SnapshotPrunerTest,SnapshotFixtureTest'
      ;;
    snapshot_too_large)
      printf '%s' 'ConversationStreamObserverFailureTest#completionRejectedByConversationServiceTransitionsToFailed'
      ;;
    history_context)
      printf '%s' 'AgentHistoryMessageMapperTest,AgentHistoryMemoryBridgeTest'
      ;;
    react_plan_regression)
      printf '%s' 'CModuleCapabilityWalkthroughTest,ReActPlanHandlerHistoryRegressionTest'
      ;;
    client_disconnect)
      printf '%s' 'MultiAgentServiceCancellationTest,CancellableAgentCallTest'
      ;;
    *) return 1 ;;
  esac
}

find_python() {
  local candidate
  for candidate in python3 python; do
    if command -v "$candidate" >/dev/null 2>&1 \
      && "$candidate" -c 'import sys; raise SystemExit(0 if sys.version_info >= (3, 9) else 1)' >/dev/null 2>&1; then
      printf '%s' "$candidate"
      return 0
    fi
  done
  return 1
}

emit_result() {
  local command="$1"
  local scenario="$2"
  local exit_code="$3"
  local status="$4"
  local reason="$5"
  local message="$6"
  local started_at="$7"
  local gate_ids="$8"
  local runner="${9:-}"
  local selector="${10:-}"
  local python_bin
  local finished_at

  finished_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  python_bin="$(find_python || true)"
  if [[ -n "$python_bin" ]]; then
    "$python_bin" - "$command" "$scenario" "$exit_code" "$status" "$reason" "$message" \
      "$started_at" "$finished_at" "$gate_ids" "$runner" "$selector" <<'PY'
import json
import sys

(command, scenario, exit_code, status, reason, message, started_at, finished_at,
 gate_ids, runner, selector) = sys.argv[1:]
details = {}
if runner:
    details["runner"] = runner
if selector:
    details["testSelector"] = selector
print(json.dumps({
    "command": command,
    "exitCode": int(exit_code),
    "startedAt": started_at,
    "finishedAt": finished_at,
    "result": {
        "status": status,
        "scenario": scenario,
        "gateIds": json.loads(gate_ids),
        "reason": reason,
        "message": message,
        "checks": [],
        "details": details,
    },
}, ensure_ascii=False, separators=(",", ":")))
PY
    return
  fi

  # 所有字段均来自受控的场景映射；没有 Python 时仍保持单行 JSON 契约。
  printf '{"command":"%s","exitCode":%s,"startedAt":"%s","finishedAt":"%s","result":{"status":"%s","scenario":"%s","gateIds":%s,"reason":"%s","message":"%s","checks":[],"details":{}}}\n' \
    "$command" "$exit_code" "$started_at" "$finished_at" "$status" "$scenario" "$gate_ids" "$reason" "$message"
}

prepare_workspace() {
  local root_dir="$1"
  local destination="$2"

  mkdir -p "$destination/docs" || return 1
  cp -a "$root_dir/genie-backend" "$destination/genie-backend" || return 1
  cp -a "$root_dir/docs/mvp-contract" "$destination/docs/mvp-contract" || return 1
}

available_maven_image() {
  local candidate
  for candidate in "$@"; do
    if [[ -n "$candidate" ]] && docker image inspect "$candidate" >/dev/null 2>&1; then
      printf '%s' "$candidate"
      return 0
    fi
  done
  return 1
}

run_docker_maven() {
  local root_dir="$1"
  local selector="$2"
  local output="$3"
  local image="$4"
  local cache_volume="${MVP_C_MAVEN_CACHE_VOLUME:-mvp-c-maven-cache}"
  local network="${MVP_C_MAVEN_NETWORK:-bridge}"

  docker run --rm --network "$network" \
    -v "$root_dir:/source:ro" \
    -v "$cache_volume:/root/.m2" \
    "$image" \
    sh -lc 'mkdir -p /work/docs && cp -a /source/genie-backend /work/genie-backend && cp -a /source/docs/mvp-contract /work/docs/mvp-contract && cd /work/genie-backend && mvn -Dstyle.color=never -Dtest="$1" test' \
    -- "$selector" >"$output" 2>&1
}

run_component_tests() {
  local root_dir="$1"
  local selector="$2"
  local workspace
  local output
  local image

  workspace="$(mktemp -d "${TMPDIR:-/tmp}/mvp-c-acceptance.XXXXXX")" || {
    RUNNER_REASON="PREREQUISITE_UNAVAILABLE"
    RUNNER_MESSAGE="temporary workspace creation failed"
    return 1
  }
  output="$workspace/maven.log"

  if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
    RUNNER_REASON="PREREQUISITE_UNAVAILABLE"
    RUNNER_MESSAGE="Docker is required for Maven component tests and is not ready"
    rm -rf "$workspace"
    return 1
  fi

  image="$(available_maven_image \
    "${MVP_C_MAVEN_IMAGE:-}" \
    'maven:3.9-eclipse-temurin-17' \
    'maven:3.9.9-eclipse-temurin-17' || true)"
  if [[ -z "$image" ]]; then
    RUNNER_REASON="PREREQUISITE_UNAVAILABLE"
    RUNNER_MESSAGE="no compatible local Maven 3.9 / Java 17 image is available; set MVP_C_MAVEN_IMAGE"
    rm -rf "$workspace"
    return 1
  fi

  RUNNER_NAME="docker-maven"
  if run_docker_maven "$root_dir" "$selector" "$output" "$image"; then
    rm -rf "$workspace"
    return 0
  fi

  RUNNER_REASON="COMPONENT_TEST_FAILED"
  RUNNER_MESSAGE="Maven component test failed in Docker"
  rm -rf "$workspace"
  return 1
}

run_real_agent_acceptance() {
  local scenario="$1"
  local script_path="$2"
  shift 2
  local script_dir
  local python_bin
  local output
  local exit_code

  script_dir="$(cd "$(dirname "$script_path")" && pwd)"
  python_bin="$(find_python || true)"
  if [[ -z "$python_bin" ]]; then
    RUNNER_REASON="PREREQUISITE_UNAVAILABLE"
    RUNNER_MESSAGE="Python 3.9 or newer is required for --real-agent"
    return 1
  fi

  set +e
  output="$("$python_bin" "$script_dir/c_acceptance.py" "$scenario" "$script_path" --real-agent "$@" 2>/dev/null)"
  exit_code=$?
  set -e
  if [[ "$output" != \{* ]]; then
    RUNNER_REASON="RUNNER_INVALID_OUTPUT"
    RUNNER_MESSAGE="real-Agent acceptance runner did not produce JSON"
    return 1
  fi
  printf '%s\n' "$output"
  return "$exit_code"
}

run_c_acceptance() {
  local scenario="$1"
  local script_path="$2"
  local root_dir
  local started_at
  local gate_ids
  local selector
  local argument
  local real_agent=0
  local forwarded=()

  shift 2
  root_dir="$(cd "$(dirname "$script_path")/../../.." && pwd)"
  started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  gate_ids="$(scenario_gate_ids "$scenario")"

  for argument in "$@"; do
    if [[ "$argument" == "--real-agent" ]]; then
      real_agent=1
    else
      forwarded+=("$argument")
    fi
  done

  if [[ "$real_agent" -eq 1 ]]; then
    RUNNER_REASON=""
    RUNNER_MESSAGE=""
    if run_real_agent_acceptance "$scenario" "$script_path" "${forwarded[@]}"; then
      return 0
    else
      local real_agent_exit=$?
    fi
    if [[ -n "$RUNNER_REASON" ]]; then
      local real_agent_status="FAIL"
      if [[ "$RUNNER_REASON" == "PREREQUISITE_UNAVAILABLE" ]]; then
        real_agent_status="BLOCKED"
      fi
      emit_result "$(basename "$script_path")" "$scenario" "$real_agent_exit" \
        "$real_agent_status" "$RUNNER_REASON" "$RUNNER_MESSAGE" \
        "$started_at" "$gate_ids" "real-agent"
    fi
    return "$real_agent_exit"
  fi

  if [[ "${#forwarded[@]}" -ne 0 ]]; then
    emit_result "$(basename "$script_path")" "$scenario" 2 "FAIL" "INVALID_INVOCATION" \
      "default component mode accepts no options; use --real-agent only for D-owned real smoke" \
      "$started_at" "$gate_ids"
    return 2
  fi

  selector="$(scenario_test_selector "$scenario" || true)"
  if [[ -z "$selector" ]]; then
    emit_result "$(basename "$script_path")" "$scenario" 2 "FAIL" "INVALID_INVOCATION" \
      "scenario has no component-test mapping" "$started_at" "$gate_ids"
    return 2
  fi

  RUNNER_REASON=""
  RUNNER_MESSAGE=""
  RUNNER_NAME=""
  if run_component_tests "$root_dir" "$selector"; then
    emit_result "$(basename "$script_path")" "$scenario" 0 "PASS" "OK" \
      "component tests passed without an application service" "$started_at" "$gate_ids" "$RUNNER_NAME" "$selector"
    return 0
  fi

  if [[ "$RUNNER_REASON" == "PREREQUISITE_UNAVAILABLE" ]]; then
    emit_result "$(basename "$script_path")" "$scenario" 1 "BLOCKED" "$RUNNER_REASON" \
      "$RUNNER_MESSAGE" "$started_at" "$gate_ids" "$RUNNER_NAME" "$selector"
  else
    emit_result "$(basename "$script_path")" "$scenario" 1 "FAIL" "${RUNNER_REASON:-COMPONENT_TEST_FAILED}" \
      "${RUNNER_MESSAGE:-Maven component test failed}" "$started_at" "$gate_ids" "$RUNNER_NAME" "$selector"
  fi
  return 1
}
