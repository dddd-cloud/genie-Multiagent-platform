#!/usr/bin/env bash
set -euo pipefail

script_name="$1"
test_selector="$2"
root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
log_file="$(mktemp "${TMPDIR:-/tmp}/genie-${script_name}.XXXXXX.log")"

finish() {
  local code="$1" status="$2"
  printf '{"script":"%s","status":"%s","exitCode":%d,"startedAt":"%s","finishedAt":"%s"}\n' \
    "$script_name" "$status" "$code" "$started_at" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  exit "$code"
}
trap 'rm -f "$log_file"' EXIT

if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
  printf '%s\n' "Docker unavailable; Maven log: $log_file" >&2
  finish 125 NOT_EXECUTED_DOCKER_UNAVAILABLE
fi
if ! command -v mvn >/dev/null 2>&1; then
  printf '%s\n' "Maven unavailable; Maven log: $log_file" >&2
  finish 127 NOT_EXECUTED_MAVEN_UNAVAILABLE
fi
if (cd "$root_dir/genie-backend" && mvn "-Dtest=$test_selector" test) >"$log_file" 2>&1; then
  finish 0 PASS
fi
printf '%s\n' "Acceptance test failed; Maven log retained at: $log_file" >&2
trap - EXIT
finish 1 FAIL
