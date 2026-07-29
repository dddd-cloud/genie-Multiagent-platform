#!/usr/bin/env bash
set -euo pipefail

SCRIPT_NAME="$(basename "$0")"
WORK_PACKAGE="MVP-B-CONVERSATION"
CHECKS=()
ERROR_CODE=""
ERROR_MESSAGE=""

json_escape() {
  local s=${1-}
  s=${s//\\/\\\\}
  s=${s//"/\\"}
  s=${s//$'\n'/\\n}
  s=${s//$'\r'/\\r}
  s=${s//$'\t'/\\t}
  printf '%s' "$s"
}

add_check() {
  CHECKS+=("$1")
}

emit_result() {
  local passed=$1
  local error_json="null"
  if [[ "$passed" != "true" ]]; then
    error_json="{\"code\":\"$(json_escape "$ERROR_CODE")\",\"message\":\"$(json_escape "$ERROR_MESSAGE")\"}"
  fi
  local checks_json="["
  local first=true
  local check
  for check in "${CHECKS[@]}"; do
    if [[ "$first" == "true" ]]; then
      first=false
    else
      checks_json+=","
    fi
    checks_json+="\"$(json_escape "$check")\""
  done
  checks_json+="]"
  printf '{"workPackage":"%s","script":"%s","passed":%s,"checks":%s,"error":%s}\n' \
    "$WORK_PACKAGE" "$SCRIPT_NAME" "$passed" "$checks_json" "$error_json"
}

fail_json() {
  ERROR_CODE=$1
  ERROR_MESSAGE=$2
  emit_result false
  exit 1
}

require_env() {
  local name=$1
  if [[ -z "${!name:-}" ]]; then
    fail_json "MISSING_ENV_${name}" "Required environment variable ${name} is not set."
  fi
}

require_command() {
  local name=$1
  command -v "$name" >/dev/null 2>&1 || fail_json "MISSING_COMMAND_${name}" "Required command ${name} is not available."
}

http_json() {
  local method=$1
  local path=$2
  local body=${3-}
  local url="${JOYAGENT_BASE_URL%/}${path}"
  local tmp_body tmp_code
  tmp_body=$(mktemp)
  tmp_code=$(mktemp)
  if [[ -n "$body" ]]; then
    curl -sS --fail-with-body -X "$method" \
      -H "Content-Type: application/json" \
      ${JOYAGENT_AUTH_HEADER:+-H "$JOYAGENT_AUTH_HEADER"} \
      --data-binary "$body" \
      -o "$tmp_body" -w '%{http_code}' "$url" > "$tmp_code" || {
        local code
        code=$(cat "$tmp_code" 2>/dev/null || true)
        rm -f "$tmp_body" "$tmp_code"
        fail_json "HTTP_${method}_FAILED" "${method} ${path} failed with HTTP ${code:-unknown}."
      }
  else
    curl -sS --fail-with-body -X "$method" \
      ${JOYAGENT_AUTH_HEADER:+-H "$JOYAGENT_AUTH_HEADER"} \
      -o "$tmp_body" -w '%{http_code}' "$url" > "$tmp_code" || {
        local code
        code=$(cat "$tmp_code" 2>/dev/null || true)
        rm -f "$tmp_body" "$tmp_code"
        fail_json "HTTP_${method}_FAILED" "${method} ${path} failed with HTTP ${code:-unknown}."
      }
  fi
  cat "$tmp_body"
  rm -f "$tmp_body" "$tmp_code"
}

json_value() {
  local expr=$1
  python -c "import json,sys; data=json.load(sys.stdin); v=${expr}; print('' if v is None else v)"
}

require_rest_env() {
  require_command curl
  require_command python
  require_env JOYAGENT_BASE_URL
}

require_probe_env() {
  require_command curl
  require_command python
  require_env MVP_B_PROBE_BASE_URL
  fail_json "PROBE_NOT_IMPLEMENTED" "This script requires the D/team acceptance harness at MVP_B_PROBE_BASE_URL; no production debug endpoint is created by MVP-B."
}