#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
MAVEN_RUNNER="${SKILL_MAVEN_RUNNER:-$ROOT/scripts/acceptance/phase2/contract/mvn-docker.sh}"
MAVEN_CMD="${SKILL_MAVEN_CMD:-}"
TMP_DIR="${SKILL_ACCEPTANCE_TMP:-$(mktemp -d)}"

run_case() {
  local name="$1"
  local selector="$2"
  if [[ -n "$MAVEN_CMD" ]]; then
    (cd "$ROOT/genie-backend" && "$MAVEN_CMD" -Dtest="$selector" test >"$TMP_DIR/$name.log" 2>&1) || {
      printf '{"name":"%s","result":"FAIL"}\n' "$name"
      return 1
    }
  elif ! bash "$MAVEN_RUNNER" -Dtest="$selector" test >"$TMP_DIR/$name.log" 2>&1; then
    printf '{"name":"%s","result":"FAIL"}\n' "$name"
    return 1
  fi
  grep -q 'BUILD SUCCESS' "$TMP_DIR/$name.log" || {
    printf '{"name":"%s","result":"FAIL"}\n' "$name"
    return 1
  }
  printf '{"name":"%s","result":"PASS"}\n' "$name"
}
