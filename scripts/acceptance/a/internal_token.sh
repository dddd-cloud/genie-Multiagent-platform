#!/usr/bin/env bash
set -euo pipefail

command_name="internal_token"
started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
result="NOT_EXECUTED"
work_dir="$(mktemp -d)"
response_file="$work_dir/response.json"
headers_file="$work_dir/headers.txt"
user_jar="$work_dir/user.cookies"
admin_jar="$work_dir/admin.cookies"

cleanup() {
  local rc=$?
  rm -rf "$work_dir"
  if [[ "$rc" -ne 0 && "$result" != "PASS" ]]; then result="FAIL"; fi
  printf '{"command":"%s","exitCode":%d,"startedAt":"%s","finishedAt":"%s","result":"%s"}\n' \
    "$command_name" "$rc" "$started_at" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$result"
}
trap cleanup EXIT

: "${APP_BASE_URL:?APP_BASE_URL is required}"
: "${GENIE_INTERNAL_AGENT_TOKEN:?GENIE_INTERNAL_AGENT_TOKEN is required}"
: "${MVP_ACCEPTANCE_USER_USERNAME:?MVP_ACCEPTANCE_USER_USERNAME is required}"
: "${MVP_ACCEPTANCE_USER_PASSWORD:?MVP_ACCEPTANCE_USER_PASSWORD is required}"
: "${MVP_ACCEPTANCE_ADMIN_USERNAME:?MVP_ACCEPTANCE_ADMIN_USERNAME is required}"
: "${MVP_ACCEPTANCE_ADMIN_PASSWORD:?MVP_ACCEPTANCE_ADMIN_PASSWORD is required}"
command -v curl >/dev/null
command -v jq >/dev/null

request() {
  local jar="$1" method="$2" path="$3" csrf="$4" payload="$5" expected_http="$6" expected_code="$7" internal_header="${8:-}"
  local args=(--silent --show-error --dump-header "$headers_file" --output "$response_file" --write-out '%{http_code}' -X "$method")
  [[ -n "$jar" ]] && args+=(--cookie "$jar" --cookie-jar "$jar")
  [[ -n "$csrf" ]] && args+=(-H "X-XSRF-TOKEN: $csrf")
  [[ -n "$internal_header" ]] && args+=(-H "X-Genie-Internal-Token: $internal_header")
  [[ -n "$payload" ]] && args+=(-H 'Content-Type: application/json' --data "$payload")
  local http
  http="$(curl "${args[@]}" "$APP_BASE_URL$path")"
  [[ "$http" == "$expected_http" ]]
  [[ "$(jq -r '.code // empty' "$response_file")" == "$expected_code" ]]
}

csrf() {
  local jar="$1"
  request "$jar" GET /api/v1/auth/csrf '' '' 200 OK
  jq -er '.data.token' "$response_file"
}

login() {
  local jar="$1" username="$2" password="$3" token
  token="$(csrf "$jar")"
  request "$jar" POST /api/v1/auth/login "$token" "$(jq -cn --arg username "$username" --arg password "$password" '{username:$username,password:$password}')" 200 OK
}

assert_invalid() {
  local jar="$1" header_value="${2:-}"
  request "$jar" POST /AutoAgent '' '{}' 401 INTERNAL_TOKEN_INVALID "$header_value"
}

assert_invalid ''
assert_invalid '' wrong-local-value
login "$user_jar" "$MVP_ACCEPTANCE_USER_USERNAME" "$MVP_ACCEPTANCE_USER_PASSWORD"
assert_invalid "$user_jar"
login "$admin_jar" "$MVP_ACCEPTANCE_ADMIN_USERNAME" "$MVP_ACCEPTANCE_ADMIN_PASSWORD"
assert_invalid "$admin_jar"

http="$(curl --silent --show-error --dump-header "$headers_file" --output "$response_file" --write-out '%{http_code}' \
  -X POST -H "X-Genie-Internal-Token: $GENIE_INTERNAL_AGENT_TOKEN" -H 'Content-Type: application/json' \
  --data '{' "$APP_BASE_URL/AutoAgent")"
[[ "$http" == "400" ]]
[[ "$(jq -r '.code // empty' "$response_file" 2>/dev/null || true)" != "INTERNAL_TOKEN_INVALID" ]]
[[ "$(jq -r '.code // empty' "$response_file" 2>/dev/null || true)" != "CSRF_INVALID" ]]
! grep -qi '^Set-Cookie: *GENIE_SESSION=' "$headers_file"
result="PASS"
