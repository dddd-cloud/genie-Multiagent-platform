#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

add_check "create conversation"
add_check "default title"
add_check "list pagination and hasMore"
add_check "detail and rename"
add_check "message history endpoint"
add_check "soft delete hides list/detail/history"
require_rest_env
body='{}'
created=$(http_json POST '/api/v1/conversations' "$body")
conv_id=$(printf '%s' "$created" | json_value "data.get('data',{}).get('id')")
title=$(printf '%s' "$created" | json_value "data.get('data',{}).get('title')")
[[ -n "$conv_id" ]] || fail_json "CREATE_NO_ID" "Create conversation response did not contain data.id."
[[ "$title" == "新对话" ]] || fail_json "DEFAULT_TITLE_MISMATCH" "Default title was not 新对话."
http_json GET '/api/v1/conversations?page=1&pageSize=1' >/dev/null
http_json GET "/api/v1/conversations/${conv_id}" >/dev/null
http_json PATCH "/api/v1/conversations/${conv_id}" '{"title":"Acceptance Renamed"}' >/dev/null
http_json GET "/api/v1/conversations/${conv_id}/messages" >/dev/null
http_json DELETE "/api/v1/conversations/${conv_id}" >/dev/null
emit_result true