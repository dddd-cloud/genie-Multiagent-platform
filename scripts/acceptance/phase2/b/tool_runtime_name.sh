#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_common.sh"
if run_maven_tests "McpRuntimeNameCollisionTest"; then json_result tool_runtime_name PASS; else json_result tool_runtime_name FAIL; exit 1; fi
