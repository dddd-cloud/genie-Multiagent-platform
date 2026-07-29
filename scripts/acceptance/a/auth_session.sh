#!/usr/bin/env bash
set -euo pipefail
started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
command_name="auth-session acceptance via APP_BASE_URL"
finish() { local code="$1" result="$2"; printf '{"command":"%s","exitCode":%s,"startedAt":"%s","finishedAt":"%s","result":"%s"}\n' "$command_name" "$code" "$started_at" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$result"; exit "$code"; }
if [[ -z "${APP_BASE_URL:-}" || -z "${MVP_ACCEPTANCE_USER_USERNAME:-}" || -z "${MVP_ACCEPTANCE_USER_PASSWORD:-}" ]]; then finish 125 "NOT_EXECUTED_REQUIRED_ENVIRONMENT_MISSING"; fi
if ! command -v curl >/dev/null 2>&1; then finish 127 "NOT_EXECUTED_CURL_UNAVAILABLE"; fi
# The credential, Cookie jar, session ID, and CSRF token are deliberately never printed.
cookie_jar="$(mktemp)"; trap 'rm -f "$cookie_jar"' EXIT
csrf_body="$(curl --silent --show-error --fail --cookie-jar "$cookie_jar" "$APP_BASE_URL/api/v1/auth/csrf")" || finish 1 "FAIL_CSRF"
csrf_token="$(printf '%s' "$csrf_body" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
[[ -n "$csrf_token" ]] || finish 1 "FAIL_CSRF"
payload=$(printf '{"username":"%s","password":"%s"}' "$MVP_ACCEPTANCE_USER_USERNAME" "$MVP_ACCEPTANCE_USER_PASSWORD")
if curl --silent --show-error --fail --cookie "$cookie_jar" --cookie-jar "$cookie_jar" -H "X-XSRF-TOKEN: $csrf_token" -H 'Content-Type: application/json' --data '{"username":"invalid-user","password":"invalid-password"}' "$APP_BASE_URL/api/v1/auth/login" >/dev/null; then finish 1 "FAIL_INVALID_CREDENTIALS"; fi
curl --silent --show-error --fail --cookie "$cookie_jar" --cookie-jar "$cookie_jar" -H "X-XSRF-TOKEN: $csrf_token" -H 'Content-Type: application/json' --data "$payload" "$APP_BASE_URL/api/v1/auth/login" >/dev/null || finish 1 "FAIL_LOGIN"
curl --silent --show-error --fail --cookie "$cookie_jar" "$APP_BASE_URL/api/v1/users/me" >/dev/null || finish 1 "FAIL_SESSION"
curl --silent --show-error --fail --cookie "$cookie_jar" -H "X-XSRF-TOKEN: $csrf_token" --data '' "$APP_BASE_URL/api/v1/auth/logout" >/dev/null || finish 1 "FAIL_LOGOUT"
if curl --silent --fail --cookie "$cookie_jar" "$APP_BASE_URL/api/v1/users/me" >/dev/null; then finish 1 "FAIL_SESSION_NOT_REVOKED"; fi
finish 0 "PASS"
