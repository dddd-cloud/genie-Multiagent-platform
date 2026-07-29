#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

add_check "only full USER/ASSISTANT COMPLETED turns"
add_check "exclude current requestId"
add_check "maxTurns and maxCharacters"
add_check "latest over-limit returns empty"
add_check "does not skip over-limit turn"
add_check "ascending return order with USER first"
add_check "tenant/owner isolation"
require_probe_env