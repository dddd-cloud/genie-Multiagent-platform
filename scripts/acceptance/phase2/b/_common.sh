#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
MAVEN_CMD="${MAVEN_CMD:-mvn}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
run_maven_tests() { local selector="$1"; (cd "$ROOT/genie-backend" && "$MAVEN_CMD" -Dtest="$selector" test >"$TMP_DIR/maven.log" 2>&1); }
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
