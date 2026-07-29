#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
BACKEND_DIR="$REPO_ROOT/genie-backend"
JAVA_HOME_WIN="${JAVA_HOME:-E:\\dev-tools\\jdk-17}"
MAVEN_CMD_WIN="${MAVEN_CMD:-E:\\dev-tools\\apache-maven-3.9.16\\bin\\mvn.cmd}"
JAVA_HOME_UNIX="$(cygpath -u "$JAVA_HOME_WIN" 2>/dev/null || printf '%s' "$JAVA_HOME_WIN")"
MAVEN_CMD_UNIX="$(cygpath -u "$MAVEN_CMD_WIN" 2>/dev/null || printf '%s' "$MAVEN_CMD_WIN")"

json_escape() {
  local value=${1-}
  value=${value//\\/\\\\}
  value=${value//"/\\"}
  value=${value//$'\n'/\\n}
  value=${value//$'\r'/\\r}
  value=${value//$'\t'/\\t}
  printf '%s' "$value"
}

fail_json() {
  local gate=$1
  local message=$2
  local duration=${3:-0}
  printf '{"gate":"%s","status":"FAIL","testsRun":0,"failures":0,"errors":1,"skipped":0,"durationMs":%s,"message":"%s"}\n' \
    "$(json_escape "$gate")" "$duration" "$(json_escape "$message")"
  exit 1
}

require_file() {
  local gate=$1
  local path=$2
  [[ -f "$path" ]] || fail_json "$gate" "Required file not found: $path"
}

require_tooling() {
  local gate=$1
  require_file "$gate" "$BACKEND_DIR/pom.xml"
  require_file "$gate" "$MAVEN_CMD_UNIX"
  require_file "$gate" "$JAVA_HOME_UNIX/bin/java.exe"
  docker version >/dev/null 2>&1 || fail_json "$gate" "Docker is not available. Start Docker Desktop and retry."
  "$JAVA_HOME_UNIX/bin/java.exe" -version >&2
  JAVA_HOME="$JAVA_HOME_WIN" PATH="$JAVA_HOME_UNIX/bin:$PATH" "$MAVEN_CMD_UNIX" -version >&2
}

sum_reports() {
  local report_dir=$1
  awk '
    /<testsuite / {
      tests = failures = errors = skipped = 0
      for (i = 1; i <= NF; i++) {
        if ($i ~ /^tests=/) { gsub(/[^0-9]/, "", $i); tests = $i }
        if ($i ~ /^failures=/) { gsub(/[^0-9]/, "", $i); failures = $i }
        if ($i ~ /^errors=/) { gsub(/[^0-9]/, "", $i); errors = $i }
        if ($i ~ /^skipped=/) { gsub(/[^0-9]/, "", $i); skipped = $i }
      }
      run += tests
      fail += failures
      err += errors
      skip += skipped
    }
    END { printf "%d %d %d %d", run, fail, err, skip }
  ' "$report_dir"/TEST-*.xml 2>/dev/null || printf '0 0 0 0'
}

run_maven_gate() {
  local gate=$1
  local test_spec=$2
  local start end duration status mvn_status counts tests failures errors skipped
  start=$(date +%s%3N)
  require_tooling "$gate"
  rm -rf "$BACKEND_DIR/target/surefire-reports"
  mkdir -p "$BACKEND_DIR/target"
  set +e
  (cd "$BACKEND_DIR" && \
    JAVA_HOME="$JAVA_HOME_WIN" PATH="$JAVA_HOME_UNIX/bin:$PATH" \
    "$MAVEN_CMD_UNIX" "-Dtest=$test_spec" -Dsurefire.useFile=false -DtrimStackTrace=false test >&2)
  mvn_status=$?
  set -e
  end=$(date +%s%3N)
  duration=$((end - start))
  counts=$(sum_reports "$BACKEND_DIR/target/surefire-reports")
  read -r tests failures errors skipped <<< "$counts"
  if [[ "$mvn_status" -eq 0 && "$failures" -eq 0 && "$errors" -eq 0 ]]; then
    status=PASS
  else
    status=FAIL
  fi
  printf '{"gate":"%s","status":"%s","testsRun":%s,"failures":%s,"errors":%s,"skipped":%s,"durationMs":%s}\n' \
    "$(json_escape "$gate")" "$status" "$tests" "$failures" "$errors" "$skipped" "$duration"
  [[ "$status" == "PASS" ]] || exit 1
}