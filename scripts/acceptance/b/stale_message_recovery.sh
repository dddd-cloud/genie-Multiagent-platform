#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

add_check "startup stale PENDING/STREAMING ASSISTANT becomes INTERRUPTED"
add_check "errorCode SERVICE_RESTARTED"
add_check "second recovery is idempotent"
add_check "COMPLETED/FAILED/USER messages unchanged"
add_check "no production debug endpoint is introduced"
require_probe_env