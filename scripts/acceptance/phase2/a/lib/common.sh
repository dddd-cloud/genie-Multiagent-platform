#!/usr/bin/env bash
set -euo pipefail

A_BASELINE_COMMIT="${A_BASELINE_COMMIT:-5056316}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
BACKEND_DIR="$REPO_ROOT/genie-backend"
UI_DIR="$REPO_ROOT/ui"
EVIDENCE_DIR="${PHASE2_A_EVIDENCE_DIR:-$BACKEND_DIR/target/phase2-a-acceptance}"
RESULTS_DIR="$EVIDENCE_DIR/results"
LOG_DIR="$EVIDENCE_DIR/logs"
SUMMARY_FILE="$EVIDENCE_DIR/summary.jsonl"
FIRST_FAILURE_FILE="$EVIDENCE_DIR/first-failure.txt"
TOOL_VERSION_FILE="$EVIDENCE_DIR/tool-versions.txt"
GATE_COUNT=0

mkdir -p "$RESULTS_DIR" "$LOG_DIR"
: > "$SUMMARY_FILE"
rm -f "$FIRST_FAILURE_FILE"

json_escape() {
  local value=${1-}
  value=${value//\\/\\\\}
  value=${value//"/\\"}
  value=${value//$'\n'/\\n}
  value=${value//$'\r'/\\r}
  value=${value//$'\t'/\\t}
  printf '%s' "$value"
}

now_utc() {
  date -u +%Y-%m-%dT%H:%M:%SZ
}

elapsed_ms() {
  local start=$1
  local end
  end=$(date +%s%3N 2>/dev/null || python -c 'import time; print(int(time.time()*1000))')
  echo $((end - start))
}

start_ms() {
  date +%s%3N 2>/dev/null || python -c 'import time; print(int(time.time()*1000))'
}

record_gate() {
  local gate_id=$1
  local gate_name=$2
  local status=$3
  local command=$4
  local tests=${5:-0}
  local failures=${6:-0}
  local errors=${7:-0}
  local skipped=${8:-0}
  local duration=${9:-0}
  local detail=${10:-}
  local file="$RESULTS_DIR/gate-${gate_id}.json"
  printf '{"gate":"%s","name":"%s","status":"%s","command":"%s","testsRun":%s,"failures":%s,"errors":%s,"skipped":%s,"durationMs":%s,"detail":"%s","timestamp":"%s"}\n' \
    "$(json_escape "$gate_id")" "$(json_escape "$gate_name")" "$status" "$(json_escape "$command")" \
    "$tests" "$failures" "$errors" "$skipped" "$duration" "$(json_escape "$detail")" "$(now_utc)" | tee "$file" >> "$SUMMARY_FILE"
  if [[ "$status" != "PASS" && ! -f "$FIRST_FAILURE_FILE" ]]; then
    printf '%s %s: %s\n' "$gate_id" "$gate_name" "$detail" > "$FIRST_FAILURE_FILE"
  fi
}

fail_gate() {
  local gate_id=$1
  local gate_name=$2
  local command=$3
  local detail=$4
  local duration=${5:-0}
  record_gate "$gate_id" "$gate_name" "FAIL" "$command" 0 0 1 0 "$duration" "$detail"
  echo "Overall: FAIL"
  exit 1
}

block_gate() {
  local gate_id=$1
  local gate_name=$2
  local command=$3
  local detail=$4
  local duration=${5:-0}
  record_gate "$gate_id" "$gate_name" "BLOCKED" "$command" 0 0 1 0 "$duration" "$detail"
  echo "Overall: BLOCKED"
  exit 2
}

to_unix_path() {
  local path=$1
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -u "$path" 2>/dev/null || printf '%s' "$path"
  else
    printf '%s' "$path"
  fi
}

java_bin_for_home() {
  local home=$1
  if [[ -x "$home/bin/java" ]]; then printf '%s' "$home/bin/java"; return 0; fi
  if [[ -x "$home/bin/java.exe" ]]; then printf '%s' "$home/bin/java.exe"; return 0; fi
  return 1
}

resolve_tooling() {
  local home candidate
  for candidate in "${JAVA_HOME:-}" "/e/dev-tools/jdk-17" "E:\\dev-tools\\jdk-17"; do
    [[ -n "$candidate" ]] || continue
    home="$(to_unix_path "$candidate")"
    if JAVA_BIN="$(java_bin_for_home "$home")"; then
      JAVA_HOME_UNIX="$home"
      break
    fi
  done
  [[ -n "${JAVA_BIN:-}" ]] || return 1

  for candidate in "${MAVEN_CMD:-}" "/e/dev-tools/apache-maven-3.9.16/bin/mvn" "/e/dev-tools/apache-maven-3.9.16/bin/mvn.cmd" "E:\\dev-tools\\apache-maven-3.9.16\\bin\\mvn.cmd"; do
    [[ -n "$candidate" ]] || continue
    candidate="$(to_unix_path "$candidate")"
    if [[ -f "$candidate" || -x "$candidate" ]]; then
      MAVEN_CMD_UNIX="$candidate"
      break
    fi
  done
  [[ -n "${MAVEN_CMD_UNIX:-}" ]] || return 1

  if ! command -v node >/dev/null 2>&1; then return 1; fi
  if ! command -v pnpm >/dev/null 2>&1; then return 1; fi
  [[ "$(pnpm --version)" == "9.15.0" ]] || return 1
}

write_tool_versions() {
  {
    echo "branch=$(git branch --show-current)"
    echo "head=$(git rev-parse HEAD)"
    echo "baseline=$A_BASELINE_COMMIT"
    echo "java_home=$JAVA_HOME_UNIX"
    "$JAVA_BIN" -version 2>&1 | sed 's/"//g'
    JAVA_HOME="$JAVA_HOME_UNIX" PATH="$(dirname "$JAVA_BIN"):$PATH" "$MAVEN_CMD_UNIX" -version | sed -n '1,3p'
    echo "node=$(node --version)"
    echo "pnpm_path=$(command -v pnpm)"
    echo "pnpm=$(pnpm --version)"
  } > "$TOOL_VERSION_FILE"
}

sum_reports() {
  local report_dir=$1
  if ! compgen -G "$report_dir/TEST-*.xml" >/dev/null; then
    printf '0 0 0 0'
    return 0
  fi
  awk '
    /<testsuite / {
      tests = failures = errors = skipped = 0
      for (i = 1; i <= NF; i++) {
        if ($i ~ /^tests=/) { gsub(/[^0-9]/, "", $i); tests = $i }
        if ($i ~ /^failures=/) { gsub(/[^0-9]/, "", $i); failures = $i }
        if ($i ~ /^errors=/) { gsub(/[^0-9]/, "", $i); errors = $i }
        if ($i ~ /^skipped=/) { gsub(/[^0-9]/, "", $i); skipped = $i }
      }
      run += tests; fail += failures; err += errors; skip += skipped
    }
    END { printf "%d %d %d %d", run, fail, err, skip }
  ' "$report_dir"/TEST-*.xml
}

run_maven_gate() {
  local gate_id=$1
  local gate_name=$2
  local selector=$3
  local command="mvn -Dtest=$selector -Dsurefire.useFile=false -DtrimStackTrace=false test"
  local start duration log status counts tests failures errors skipped mvn_status
  echo "==> ${gate_id} ${gate_name}"
  start=$(start_ms)
  log="$LOG_DIR/gate-${gate_id}-maven.log"
  rm -rf "$BACKEND_DIR/target/surefire-reports"
  set +e
  (cd "$BACKEND_DIR" && JAVA_HOME="$JAVA_HOME_UNIX" "$MAVEN_CMD_UNIX" "-Dtest=$selector" -Dsurefire.useFile=false -DtrimStackTrace=false test) 2>&1 | tee "$log"
  mvn_status=${PIPESTATUS[0]}
  set -e
  duration=$(elapsed_ms "$start")
  counts=$(sum_reports "$BACKEND_DIR/target/surefire-reports")
  read -r tests failures errors skipped <<< "$counts"
  if [[ "$tests" -eq 0 ]]; then
    fail_gate "$gate_id" "$gate_name" "$command" "Maven selected zero tests" "$duration"
  fi
  if [[ "$mvn_status" -eq 0 && "$failures" -eq 0 && "$errors" -eq 0 ]]; then
    status=PASS
  else
    status=FAIL
  fi
  record_gate "$gate_id" "$gate_name" "$status" "$command" "$tests" "$failures" "$errors" "$skipped" "$duration" "surefire reports collected"
  [[ "$status" == "PASS" ]] || { echo "Overall: FAIL"; exit 1; }
}

run_shell_gate() {
  local gate_id=$1
  local gate_name=$2
  shift 2
  local command="$*"
  local start duration log exit_code
  echo "==> ${gate_id} ${gate_name}"
  start=$(start_ms)
  log="$LOG_DIR/gate-${gate_id}-shell.log"
  set +e
  "$@" 2>&1 | tee "$log"
  exit_code=${PIPESTATUS[0]}
  set -e
  duration=$(elapsed_ms "$start")
  if [[ "$exit_code" -eq 0 ]]; then
    record_gate "$gate_id" "$gate_name" "PASS" "$command" 0 0 0 0 "$duration" "command completed"
  else
    record_gate "$gate_id" "$gate_name" "FAIL" "$command" 0 0 1 0 "$duration" "command exited $exit_code"
    echo "Overall: FAIL"
    exit 1
  fi
}

branch_changed_files() {
  git diff --name-only "$A_BASELINE_COMMIT..HEAD"
  git diff --name-only
  git diff --cached --name-only
  git ls-files --others --exclude-standard
}

verify_boundary() {
  local bad=0 path
  while IFS= read -r path; do
    [[ -n "$path" ]] || continue
    case "$path" in
      genie-backend/src/main/java/com/jd/genie/platform/phase2/configuration/*|genie-backend/src/main/java/com/jd/genie/platform/phase2/configuration/**) ;;
      genie-backend/src/test/java/com/jd/genie/platform/phase2/configuration/*|genie-backend/src/test/java/com/jd/genie/platform/phase2/configuration/**) ;;
      genie-backend/src/main/resources/db/migration/V004__agent_and_skill.sql) ;;
      genie-backend/src/main/resources/application.yml) ;;
      genie-backend/src/main/java/com/jd/genie/agent/prompt/PlanningPrompt.java) ;;
      genie-backend/src/main/java/com/jd/genie/agent/prompt/ToolCallPrompt.java) ;;
      genie-backend/src/test/java/com/jd/genie/platform/phase2contract/Phase2SecurityIntegrationTest.java) ;;
      scripts/acceptance/phase2/a/*|scripts/acceptance/phase2/a/**) ;;
      *) echo "Boundary violation: $path"; bad=1 ;;
    esac
  done < <(branch_changed_files | sort -u)
  [[ "$bad" -eq 0 ]]
}

verify_forbidden_paths() {
  local bad=0 path
  while IFS= read -r path; do
    [[ -n "$path" ]] || continue
    case "$path" in
      docs/mvp-contract/phase2/*|docs/mvp-contract/phase2/**|genie-backend/src/main/java/com/jd/genie/platform/phase2contract/*|genie-backend/src/main/java/com/jd/genie/platform/phase2contract/**|genie-backend/src/main/java/com/jd/genie/platform/MvpErrorCode.java|genie-backend/src/main/java/com/jd/genie/platform/security/SecurityConfig.java|genie-backend/src/main/java/com/jd/genie/config/GenieConfig.java|Makefile|deploy/*|deploy/**|ui/*|ui/**|.env|.env.example)
        echo "Forbidden changed path: $path"; bad=1 ;;
      genie-backend/src/main/resources/db/migration/V001*|genie-backend/src/main/resources/db/migration/V002*|genie-backend/src/main/resources/db/migration/V003*|genie-backend/src/main/resources/db/migration/V005*)
        echo "Forbidden migration changed: $path"; bad=1 ;;
      genie-backend/src/main/java/com/jd/genie/platform/conversation/*|genie-backend/src/main/java/com/jd/genie/platform/conversation/**|genie-backend/src/main/java/com/jd/genie/platform/agentbridge/*|genie-backend/src/main/java/com/jd/genie/platform/agentbridge/**|genie-backend/src/main/java/com/jd/genie/platform/phase2runtime/*|genie-backend/src/main/java/com/jd/genie/platform/phase2runtime/**|genie-backend/src/main/java/com/jd/genie/platform/orchestration/*|genie-backend/src/main/java/com/jd/genie/platform/orchestration/**|genie-backend/src/main/java/com/jd/genie/platform/tooling/*|genie-backend/src/main/java/com/jd/genie/platform/tooling/**)
        echo "Forbidden module changed: $path"; bad=1 ;;
    esac
  done < <(branch_changed_files | sort -u)
  [[ "$bad" -eq 0 ]]
}

verify_secret_scan() {
  local bad=0 path pattern
  local patterns=(
    'Authorization:'
    'Bearer[[:space:]]+[A-Za-z0-9._~+/=-]{16,}'
    '-----BEGIN[[:space:]]+(RSA|OPENSSH|EC|DSA)?[[:space:]]*PRIVATE KEY-----'
    '(api[_-]?key|password|token|secret)[[:space:]]*=[[:space:]]*[^$<{(][^[:space:]]{8,}'
    'System\.out\.(print|println).*Prompt'
    'System\.out\.(print|println).*Memory'
    'System\.out\.(print|println).*Summary'
    'BeanUtils\.copyProperties'
    'REQUIRES_NEW'
    '/api/v2/agents/\{id\}/test'
  )
  while IFS= read -r path; do
    [[ -n "$path" ]] || continue
    [[ -f "$REPO_ROOT/$path" ]] || continue
    case "$path" in .env|*.env) echo "Secret scan refuses env file: $path"; bad=1; continue ;; esac
    for pattern in "${patterns[@]}"; do
      if [[ "$path" == scripts/acceptance/phase2/a/* && ( "$pattern" == "Authorization:" || "$pattern" == "REQUIRES_NEW" || "$pattern" == "BeanUtils\\.copyProperties" || "$pattern" == "/api/v2/agents/\\{id\\}/test" ) ]]; then
        continue
      fi
      if grep -E -I -q -- "$pattern" "$REPO_ROOT/$path"; then
        if [[ "$pattern" == *secret* && "$path" == *Test.java ]]; then
          continue
        fi
        echo "Secret/boundary pattern hit: $path :: $pattern"
        bad=1
      fi
    done
  done < <(branch_changed_files | sort -u)
  [[ "$bad" -eq 0 ]]
}

verify_prompt_generalization_static() {
  local bad=0 path pattern
  local files=(
    "$REPO_ROOT/genie-backend/src/main/resources/application.yml"
    "$REPO_ROOT/genie-backend/src/main/java/com/jd/genie/agent/prompt/PlanningPrompt.java"
    "$REPO_ROOT/genie-backend/src/main/java/com/jd/genie/agent/prompt/ToolCallPrompt.java"
  )
  local patterns=(
    'chain of thought'
    'Chain-of-thought'
    '投资者情绪'
    '财报'
    '估值'
    '固定搜索次数'
    '必须输出HTML'
    '必须使用中文'
  )
  for path in "${files[@]}"; do
    [[ -f "$path" ]] || { echo "Missing prompt file: $path"; bad=1; continue; }
    for pattern in "${patterns[@]}"; do
      if grep -I -q "$pattern" "$path"; then
        echo "Forbidden prompt specialization hit: ${path#$REPO_ROOT/} :: $pattern"
        bad=1
      fi
    done
  done
  [[ "$bad" -eq 0 ]]
}

verify_evidence_files() {
  local expected=11
  local count
  count=$(find "$RESULTS_DIR" -name 'gate-*.json' -type f | wc -l | tr -d ' ')
  [[ "$count" -ge "$expected" ]] || { echo "Expected at least $expected evidence files, got $count"; return 1; }
  if grep -R -E -I '(Authorization:|Bearer[[:space:]]+|API[_-]?KEY=|PASSWORD=|TOKEN=|Cookie:)' "$EVIDENCE_DIR" >/dev/null 2>&1; then
    echo "Sensitive marker found in evidence output"
    return 1
  fi
  return 0
}