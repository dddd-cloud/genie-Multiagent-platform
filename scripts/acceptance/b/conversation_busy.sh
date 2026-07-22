#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

add_check "PENDING ASSISTANT blocks new execution"
add_check "STREAMING ASSISTANT blocks new execution"
add_check "terminal ASSISTANT does not block"
add_check "delete active conversation returns CONVERSATION_BUSY"
require_probe_env