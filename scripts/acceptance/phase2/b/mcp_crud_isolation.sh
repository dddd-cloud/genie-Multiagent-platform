#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_common.sh"
if run_maven_tests "McpServerOwnershipTest,McpServerVersionConflictTest"; then json_result mcp_crud_isolation PASS; else json_result mcp_crud_isolation FAIL; exit 1; fi
