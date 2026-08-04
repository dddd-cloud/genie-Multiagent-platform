#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_common.sh"
if rg -n --hidden -g '*.log' -g '*.out' -g '*.err' '(GENIE_INTERNAL_MCP_TOKEN=|Authorization: Bearer [A-Za-z0-9._-]+|passwordHash|X-Server-Keys:)' "$ROOT/genie-client" "$ROOT/genie-backend" >/dev/null; then
  printf '%s\n' '{"name":"log_secret_scan","result":"FAIL"}'
  exit 1
fi
json_result log_secret_scan PASS
