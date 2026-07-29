#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

add_check "User A/User B list isolation"
add_check "cross-owner detail/history/rename/delete hidden as RESOURCE_NOT_FOUND"
add_check "cross-tenant access hidden as RESOURCE_NOT_FOUND"
add_check "prepareExecution isolation requires team probe harness"
require_command curl
require_command python
require_env JOYAGENT_BASE_URL
require_env JOYAGENT_USER_A_AUTH_HEADER
require_env JOYAGENT_USER_B_AUTH_HEADER
ERROR_CODE="REQUIRES_MULTI_USER_FIXTURE"
ERROR_MESSAGE="Run this script in the D/team acceptance environment with pre-created User A/User B and tenant fixtures; this standalone script will not mint credentials."
emit_result false
exit 1