#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

add_check "same conversationId/requestId first prepare succeeds"
add_check "second prepare returns DUPLICATE_REQUEST"
add_check "duplicate priority before busy"
add_check "single USER/ASSISTANT pair and nextTurnNo advances once"
require_probe_env