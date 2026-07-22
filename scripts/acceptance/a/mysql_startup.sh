#!/usr/bin/env bash
set -uo pipefail

started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
command_name="mvn -q -Dtest=MySqlFlywayMigrationTest test"

finish() {
  local exit_code="$1"
  local result="$2"
  local finished_at
  finished_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf '{"command":"%s","exitCode":%s,"startedAt":"%s","finishedAt":"%s","result":"%s"}\n' \
    "$command_name" "$exit_code" "$started_at" "$finished_at" "$result"
  exit "$exit_code"
}

if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
  finish 125 "NOT_EXECUTED_DOCKER_UNAVAILABLE"
fi

if ! command -v mvn >/dev/null 2>&1; then
  finish 127 "NOT_EXECUTED_MAVEN_UNAVAILABLE"
fi

if (cd genie-backend && mvn -q -Dtest=MySqlFlywayMigrationTest test) >/dev/null 2>&1; then
  finish 0 "PASS"
fi

finish 1 "FAIL"
