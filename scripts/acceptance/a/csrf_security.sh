#!/usr/bin/env bash
set -euo pipefail

started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
command_name="mvn -q -Dtest=SecurityCsrfIntegrationTest,AdminUserManagementIntegrationTest,InternalAgentSecurityIntegrationTest test"
finish() { local code="$1" result="$2"; printf '{"command":"%s","exitCode":%s,"startedAt":"%s","finishedAt":"%s","result":"%s"}\n' "$command_name" "$code" "$started_at" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$result"; exit "$code"; }
if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then finish 125 "NOT_EXECUTED_DOCKER_UNAVAILABLE"; fi
if ! command -v mvn >/dev/null 2>&1; then finish 127 "NOT_EXECUTED_MAVEN_UNAVAILABLE"; fi
if (cd genie-backend && mvn -q -Dtest=SecurityCsrfIntegrationTest,AdminUserManagementIntegrationTest,InternalAgentSecurityIntegrationTest test) >/dev/null 2>&1; then finish 0 "PASS"; fi
finish 1 "FAIL"
