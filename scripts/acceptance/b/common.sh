#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
BACKEND_DIR="$REPO_ROOT/genie-backend"

to_unix_path() {
  local path=$1
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -u "$path" 2>/dev/null || printf '%s' "$path"
  else
    printf '%s' "$path"
  fi
}

to_win_path() {
  local path=$1
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -w "$path" 2>/dev/null || printf '%s' "$path"
  else
    printf '%s' "$path"
  fi
}

java_bin_for_home() {
  local home=$1
  if [[ -x "$home/bin/java" ]]; then
    printf '%s' "$home/bin/java"
    return 0
  fi
  if [[ -x "$home/bin/java.exe" ]]; then
    printf '%s' "$home/bin/java.exe"
    return 0
  fi
  return 1
}

resolve_java_home() {
  local candidate home bin
  for candidate in \
    "${JAVA_HOME:-}" \
    "E:\\dev-tools\\jdk-17" \
    "/c/Program Files/Java/jdk-21" \
    "/c/Program Files/Java/jdk-17"; do
    [[ -n "$candidate" ]] || continue
    home="$(to_unix_path "$candidate")"
    if bin="$(java_bin_for_home "$home")"; then
      JAVA_HOME_UNIX="$home"
      JAVA_HOME_WIN="$(to_win_path "$home")"
      JAVA_BIN="$bin"
      return 0
    fi
  done

  if command -v java >/dev/null 2>&1; then
    bin="$(command -v java)"
    # Prefer JAVA_HOME derived from a real java binary when possible.
    if [[ -x "$(dirname "$bin")/../bin/java" ]] || [[ -x "$(dirname "$bin")/java" ]]; then
      home="$(cd "$(dirname "$bin")/.." && pwd)"
      if java_bin_for_home "$home" >/dev/null 2>&1; then
        JAVA_HOME_UNIX="$home"
        JAVA_HOME_WIN="$(to_win_path "$home")"
        JAVA_BIN="$(java_bin_for_home "$home")"
        return 0
      fi
    fi
    JAVA_BIN="$bin"
    JAVA_HOME_UNIX="${JAVA_HOME:-}"
    JAVA_HOME_WIN="${JAVA_HOME:-}"
    return 0
  fi
  return 1
}

resolve_maven_cmd() {
  local candidate unix_path
  for candidate in \
    "${MAVEN_CMD:-}" \
    "E:\\dev-tools\\apache-maven-3.9.16\\bin\\mvn.cmd"; do
    [[ -n "$candidate" ]] || continue
    unix_path="$(to_unix_path "$candidate")"
    if [[ -f "$unix_path" || -x "$unix_path" ]]; then
      MAVEN_CMD_UNIX="$unix_path"
      MAVEN_CMD_WIN="$(to_win_path "$unix_path")"
      return 0
    fi
  done

  if command -v mvn >/dev/null 2>&1; then
    MAVEN_CMD_UNIX="$(command -v mvn)"
    MAVEN_CMD_WIN="$(to_win_path "$MAVEN_CMD_UNIX")"
    return 0
  fi
  return 1
}

JAVA_HOME_UNIX=""
JAVA_HOME_WIN=""
JAVA_BIN=""
MAVEN_CMD_UNIX=""
MAVEN_CMD_WIN=""

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
  resolve_java_home || fail_json "$gate" "Java runtime not found. Set JAVA_HOME or put java on PATH."
  resolve_maven_cmd || fail_json "$gate" "Maven not found. Set MAVEN_CMD or put mvn on PATH."
  docker version >/dev/null 2>&1 || fail_json "$gate" "Docker is not available. Start Docker Desktop and retry."
  "$JAVA_BIN" -version >&2
  JAVA_HOME="$JAVA_HOME_WIN" PATH="$(dirname "$JAVA_BIN"):$PATH" "$MAVEN_CMD_UNIX" -version >&2
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
  start=$(date +%s%3N 2>/dev/null || python -c 'import time; print(int(time.time()*1000))')
  require_tooling "$gate"
  rm -rf "$BACKEND_DIR/target/surefire-reports"
  mkdir -p "$BACKEND_DIR/target"
  set +e
  (cd "$BACKEND_DIR" && \
    JAVA_HOME="$JAVA_HOME_WIN" PATH="$(dirname "$JAVA_BIN"):$PATH" \
    "$MAVEN_CMD_UNIX" "-Dtest=$test_spec" -Dsurefire.useFile=false -DtrimStackTrace=false test >&2)
  mvn_status=$?
  set -e
  end=$(date +%s%3N 2>/dev/null || python -c 'import time; print(int(time.time()*1000))')
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
