#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
MAVEN_CMD="${MAVEN_CMD:-mvn}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
run_maven_tests() {
  local selector="$1"
  if [[ "$MAVEN_CMD" == *.cmd ]]; then
    local windows_maven="$MAVEN_CMD"
    if command -v cygpath >/dev/null 2>&1; then windows_maven="$(cygpath -w "$MAVEN_CMD")"; fi
    export PHASE2_MAVEN_CMD="$windows_maven" PHASE2_SELECTOR="$selector" PHASE2_WORKDIR="$ROOT/genie-backend" PHASE2_LOG="$TMP_DIR/maven.log" PHASE2_ERR="$TMP_DIR/maven.err"
    powershell.exe -NoProfile -Command '$p=Start-Process -FilePath $env:PHASE2_MAVEN_CMD -ArgumentList @(("-Dtest=" + $env:PHASE2_SELECTOR),"test") -WorkingDirectory $env:PHASE2_WORKDIR -RedirectStandardOutput $env:PHASE2_LOG -RedirectStandardError $env:PHASE2_ERR -Wait -PassThru; if($p.ExitCode -ne 0){exit $p.ExitCode}'
    cat "$TMP_DIR/maven.err" >>"$TMP_DIR/maven.log" 2>/dev/null || true
  else
    (cd "$ROOT/genie-backend" && "$MAVEN_CMD" -Dtest="$selector" test >"$TMP_DIR/maven.log" 2>&1)
  fi
}
run_python_tests() {
  if [[ "${PYTHON_USE_DOCKER:-0}" == "1" ]] && command -v docker >/dev/null 2>&1; then
    MSYS_NO_PATHCONV=1 docker run --rm -v "$ROOT/genie-client:/app" -w /app python:3.11-slim sh -c "pip install -q fastapi==0.115.12 mcp==1.9.4 && python -m unittest discover -s tests -p 'test_*.py'" >"$TMP_DIR/python.log" 2>&1
  elif [[ -n "${PYTHON_CMD:-}" ]]; then
    (cd "$ROOT/genie-client" && "$PYTHON_CMD" -m unittest discover -s tests -p 'test_*.py' >"$TMP_DIR/python.log" 2>&1)
  elif command -v python >/dev/null 2>&1; then
    (cd "$ROOT/genie-client" && python -m unittest discover -s tests -p 'test_*.py' >"$TMP_DIR/python.log" 2>&1)
  elif command -v docker >/dev/null 2>&1; then
    MSYS_NO_PATHCONV=1 docker run --rm -v "$ROOT/genie-client:/app" -w /app python:3.11-slim sh -c "pip install -q fastapi==0.115.12 mcp==1.9.4 && python -m unittest discover -s tests -p 'test_*.py'" >"$TMP_DIR/python.log" 2>&1
  else
    return 127
  fi
}
json_result() { printf '{"name":"%s","result":"%s"}\n' "$1" "$2"; }
