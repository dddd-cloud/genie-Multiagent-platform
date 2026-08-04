#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}"

BASELINE_FILE="docs/mvp-contract/phase2/protected-baseline.sha256"
GENERATE_SCRIPT="scripts/acceptance/phase2/contract/generate_protected_baseline.sh"

overall_blocked() {
  echo "Overall: BLOCKED"
  exit 2
}

overall_fail() {
  echo "Overall: FAIL"
  exit 1
}

if ! command -v git >/dev/null 2>&1; then
  echo "BLOCKED: git is required"
  overall_blocked
fi

if ! command -v sha256sum >/dev/null 2>&1; then
  echo "BLOCKED: sha256sum is required"
  overall_blocked
fi

if [[ ! -f "${BASELINE_FILE}" ]]; then
  echo "FAIL: missing ${BASELINE_FILE}"
  overall_fail
fi

if [[ ! -f "${GENERATE_SCRIPT}" ]]; then
  echo "FAIL: missing ${GENERATE_SCRIPT}"
  overall_fail
fi

baseline_wt="$(cat "${BASELINE_FILE}")"
baseline_wt_bytes="$(printf '%s' "${baseline_wt}" | wc -c | tr -d ' ')"
if [[ "${baseline_wt_bytes}" -eq 0 ]]; then
  echo "FAIL: baseline is empty"
  overall_fail
fi

if grep -q $'\r' "${BASELINE_FILE}"; then
  echo "FAIL: baseline working tree contains CR"
  overall_fail
fi

if ! git cat-file -e "HEAD:${BASELINE_FILE}" 2>/dev/null; then
  echo "FAIL: baseline missing from HEAD: ${BASELINE_FILE}"
  overall_fail
fi

baseline_blob="$(git cat-file blob "$(git rev-parse "HEAD:${BASELINE_FILE}")")"
if printf '%s' "${baseline_blob}" | grep -q $'\r'; then
  echo "FAIL: baseline HEAD blob contains CR"
  overall_fail
fi

# Prefer working-tree baseline for verification content once CR-free;
# path set must still match HEAD-list and hashes come from HEAD blobs.
mapfile -t expected_files < <(bash "${GENERATE_SCRIPT}" --list)
declare -A expected_set=()
for path in "${expected_files[@]}"; do
  expected_set["${path}"]=1
done

if [[ "${#expected_files[@]}" -ne 18 ]]; then
  echo "FAIL: generate --list must return exactly 18 paths"
  overall_fail
fi

mapfile -t baseline_lines < <(grep -v '^[[:space:]]*$' "${BASELINE_FILE}" || true)
if [[ "${#baseline_lines[@]}" -ne 18 ]]; then
  echo "FAIL: baseline must contain exactly 18 non-empty entries, found ${#baseline_lines[@]}"
  overall_fail
fi

declare -A seen_paths=()
declare -A seen_lines=()
line_no=0
for line in "${baseline_lines[@]}"; do
  line_no=$((line_no + 1))
  if [[ ! "${line}" =~ ^([0-9a-fA-F]{64})[[:space:]]{2}(.+)$ ]]; then
    echo "FAIL: malformed baseline line ${line_no}: expected '<64-hex><two-spaces><path>'"
    overall_fail
  fi
  digest="${BASH_REMATCH[1]}"
  path="${BASH_REMATCH[2]}"

  if [[ "${path}" == /* || "${path}" =~ ^[A-Za-z]: ]]; then
    echo "FAIL: absolute path not allowed at line ${line_no}: ${path}"
    overall_fail
  fi

  if [[ -n "${seen_paths[${path}]+x}" ]]; then
    echo "FAIL: duplicate baseline path: ${path}"
    overall_fail
  fi
  seen_paths["${path}"]=1

  if [[ -n "${seen_lines[${line}]+x}" ]]; then
    echo "FAIL: duplicate baseline digest line at ${line_no}"
    overall_fail
  fi
  seen_lines["${line}"]=1

  expected_path="${expected_files[$((line_no - 1))]}"
  if [[ "${path}" != "${expected_path}" ]]; then
    echo "FAIL: baseline path order mismatch at line ${line_no}: expected ${expected_path}, got ${path}"
    overall_fail
  fi

  if [[ -z "${expected_set[${path}]+x}" ]]; then
    echo "FAIL: baseline path not in protected set: ${path}"
    overall_fail
  fi

  git cat-file -e "HEAD:${path}" 2>/dev/null || {
    echo "FAIL: protected path missing from HEAD: ${path}"
    overall_fail
  }

  actual="$(
    git cat-file blob "$(git rev-parse "HEAD:${path}")" |
      sha256sum |
      awk '{print $1}'
  )"
  if [[ "${actual}" != "${digest}" ]]; then
    echo "FAIL: hash mismatch for ${path}"
    echo "  baseline: ${digest}"
    echo "  HEAD:     ${actual}"
    overall_fail
  fi
  echo "${path}: OK"
done

if [[ "${#seen_paths[@]}" -ne 18 ]]; then
  echo "FAIL: baseline path set size is ${#seen_paths[@]}, expected 18"
  overall_fail
fi

git diff --quiet -- "${expected_files[@]}" || {
  echo "FAIL: protected files have unstaged changes"
  overall_fail
}

git diff --cached --quiet -- "${expected_files[@]}" || {
  echo "FAIL: protected files have staged changes"
  overall_fail
}

echo "PASS: protected baseline verified against Git HEAD blobs"
exit 0
