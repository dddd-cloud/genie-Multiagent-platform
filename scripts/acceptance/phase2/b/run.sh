#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
scripts=(migration_v005.sh mcp_crud_isolation.sh credential_encryption_no_echo.sh ssrf_rebinding_rejection.sh discovery_atomic_availability.sh tool_runtime_name.sh tool_binding_allowlist.sh runtime_tool_collection_fake.sh unauthorized_tool_block.sh genie_client_internal_auth.sh log_secret_scan.sh)
for script in "${scripts[@]}"; do
  if ! bash "$ROOT/scripts/acceptance/phase2/b/$script" >"$TMP_DIR/$script.json" 2>&1; then
    printf '{"name":"phase2_b_acceptance","result":"FAIL","failed":"%s"}\n' "$script"
    exit 1
  fi
  if [[ "$(wc -l <"$TMP_DIR/$script.json")" -ne 1 ]] || ! grep -Eq '^\{"name":"[^"]+","result":"PASS"\}$' "$TMP_DIR/$script.json"; then
    printf '{"name":"phase2_b_acceptance","result":"FAIL","failed":"%s-invalid-json"}\n' "$script"
    exit 1
  fi
done
printf '{"name":"phase2_b_acceptance","result":"PASS","scripts":%d}\n' "${#scripts[@]}"
