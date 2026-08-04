#!/usr/bin/env bash
set -euo pipefail
if rg -n --hidden -g '*.log' -g '*.out' '(GENIE_INTERNAL_MCP_TOKEN=|Authorization: Bearer [A-Za-z0-9._-]+|passwordHash|X-Server-Keys:)' . >/dev/null; then
  printf '%s\n' '{"name":"log_secret_scan","result":"FAIL"}'
  exit 1
fi
printf '%s\n' '{"name":"log_secret_scan","result":"PASS"}'
