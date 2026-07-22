#!/usr/bin/env bash
set -euo pipefail

command_name="user_admin"
started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
result="NOT_EXECUTED"
work_dir="$(mktemp -d)"
admin_jar="$work_dir/admin.cookies"
target_a_jar="$work_dir/target-a.cookies"
target_b_jar="$work_dir/target-b.cookies"
target_new_jar="$work_dir/target-new.cookies"
body_file="$work_dir/body.json"

cleanup() {
  rc=$?
  rm -rf "$work_dir"
  if [[ "$rc" -ne 0 && "$result" != "PASS" ]]; then result="FAIL"; fi
  printf '{"command":"%s","exitCode":%d,"startedAt":"%s","finishedAt":"%s","result":"%s"}\n' \
    "$command_name" "$rc" "$started_at" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$result"
}
trap cleanup EXIT

: "${APP_BASE_URL:?APP_BASE_URL is required}"
: "${MVP_ACCEPTANCE_ADMIN_USERNAME:?MVP_ACCEPTANCE_ADMIN_USERNAME is required}"
: "${MVP_ACCEPTANCE_ADMIN_PASSWORD:?MVP_ACCEPTANCE_ADMIN_PASSWORD is required}"
: "${MVP_ACCEPTANCE_TARGET_USERNAME:?MVP_ACCEPTANCE_TARGET_USERNAME is required}"
: "${MVP_ACCEPTANCE_TARGET_DISPLAY_NAME:?MVP_ACCEPTANCE_TARGET_DISPLAY_NAME is required}"
: "${MVP_ACCEPTANCE_TARGET_PASSWORD:?MVP_ACCEPTANCE_TARGET_PASSWORD is required}"
: "${MVP_ACCEPTANCE_TARGET_NEW_PASSWORD:?MVP_ACCEPTANCE_TARGET_NEW_PASSWORD is required}"
command -v curl >/dev/null
command -v jq >/dev/null

request() {
  local jar="$1" method="$2" path="$3" csrf="${4:-}" payload="${5:-}" expected_http="$6" expected_code="$7"
  local args=(--silent --show-error --output "$body_file" --write-out '%{http_code}' --cookie "$jar" --cookie-jar "$jar" -X "$method")
  [[ -n "$csrf" ]] && args+=(-H "X-XSRF-TOKEN: $csrf")
  [[ -n "$payload" ]] && args+=(-H 'Content-Type: application/json' --data "$payload")
  local http
  http="$(curl "${args[@]}" "$APP_BASE_URL$path")"
  [[ "$http" == "$expected_http" ]] || return 1
  [[ "$(jq -r '.code // empty' "$body_file")" == "$expected_code" ]] || return 1
}

csrf() {
  local jar="$1"
  request "$jar" GET /api/v1/auth/csrf '' '' 200 OK
  jq -er '.data.token' "$body_file"
}

login() {
  local jar="$1" username="$2" password="$3" token
  token="$(csrf "$jar")"
  request "$jar" POST /api/v1/auth/login "$token" "$(jq -cn --arg username "$username" --arg password "$password" '{username:$username,password:$password}')" 200 OK
}

assert_me_fails() {
  local jar="$1"
  request "$jar" GET /api/v1/users/me '' '' 401 AUTH_REQUIRED
}

login "$admin_jar" "$MVP_ACCEPTANCE_ADMIN_USERNAME" "$MVP_ACCEPTANCE_ADMIN_PASSWORD"
admin_csrf="$(csrf "$admin_jar")"
create_payload="$(jq -cn --arg username "$MVP_ACCEPTANCE_TARGET_USERNAME" --arg displayName "$MVP_ACCEPTANCE_TARGET_DISPLAY_NAME" --arg password "$MVP_ACCEPTANCE_TARGET_PASSWORD" '{username:$username,displayName:$displayName,password:$password,role:"USER"}')"
request "$admin_jar" POST /api/v1/admin/users "$admin_csrf" "$create_payload" 200 OK
target_id="$(jq -er '.data.id' "$body_file")"
request "$admin_jar" GET '/api/v1/admin/users?page=1&pageSize=100' '' '' 200 OK
jq -e --arg id "$target_id" '.data.items[] | select(.id == $id and .status == "ACTIVE")' "$body_file" >/dev/null

login "$target_a_jar" "$MVP_ACCEPTANCE_TARGET_USERNAME" "$MVP_ACCEPTANCE_TARGET_PASSWORD"
target_csrf="$(csrf "$target_a_jar")"
request "$target_a_jar" GET /api/v1/admin/users "$target_csrf" '' 403 ACCESS_DENIED
login "$target_b_jar" "$MVP_ACCEPTANCE_TARGET_USERNAME" "$MVP_ACCEPTANCE_TARGET_PASSWORD"

request "$admin_jar" PATCH "/api/v1/admin/users/$target_id/status" "$admin_csrf" '{"status":"DISABLED"}' 200 OK
assert_me_fails "$target_a_jar"
assert_me_fails "$target_b_jar"
disabled_token="$(csrf "$target_new_jar")"
request "$target_new_jar" POST /api/v1/auth/login "$disabled_token" "$(jq -cn --arg username "$MVP_ACCEPTANCE_TARGET_USERNAME" --arg password "$MVP_ACCEPTANCE_TARGET_PASSWORD" '{username:$username,password:$password}')" 401 AUTH_INVALID_CREDENTIALS

request "$admin_jar" PATCH "/api/v1/admin/users/$target_id/status" "$admin_csrf" '{"status":"ACTIVE"}' 200 OK
assert_me_fails "$target_a_jar"
assert_me_fails "$target_b_jar"
login "$target_new_jar" "$MVP_ACCEPTANCE_TARGET_USERNAME" "$MVP_ACCEPTANCE_TARGET_PASSWORD"

request "$admin_jar" POST "/api/v1/admin/users/$target_id/reset-password" "$admin_csrf" "$(jq -cn --arg newPassword "$MVP_ACCEPTANCE_TARGET_NEW_PASSWORD" '{newPassword:$newPassword}')" 200 OK
jq -e '.data == null' "$body_file" >/dev/null
assert_me_fails "$target_new_jar"
old_password_token="$(csrf "$target_a_jar")"
request "$target_a_jar" POST /api/v1/auth/login "$old_password_token" "$(jq -cn --arg username "$MVP_ACCEPTANCE_TARGET_USERNAME" --arg password "$MVP_ACCEPTANCE_TARGET_PASSWORD" '{username:$username,password:$password}')" 401 AUTH_INVALID_CREDENTIALS
login "$target_b_jar" "$MVP_ACCEPTANCE_TARGET_USERNAME" "$MVP_ACCEPTANCE_TARGET_NEW_PASSWORD"
result="PASS"
