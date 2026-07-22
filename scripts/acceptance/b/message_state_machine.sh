#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

add_check "PENDING to STREAMING"
add_check "STREAMING to COMPLETED"
add_check "PENDING/STREAMING to FAILED or INTERRUPTED"
add_check "terminal status cannot be overwritten"
add_check "invalid transition returns MESSAGE_STATE_CONFLICT"
add_check "invalid complete snapshot keeps STREAMING"
add_check "invalid partial snapshot is tolerated for fail/interrupt"
require_probe_env