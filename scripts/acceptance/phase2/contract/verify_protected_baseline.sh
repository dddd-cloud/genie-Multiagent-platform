#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}"

BASELINE_FILE="docs/mvp-contract/phase2/protected-baseline.sha256"

if [[ ! -f "${BASELINE_FILE}" ]]; then
  echo "FAIL: missing ${BASELINE_FILE}"
  exit 1
fi

if ! command -v sha256sum >/dev/null 2>&1; then
  echo "BLOCKED: sha256sum is required"
  exit 2
fi

sha256sum --check "${BASELINE_FILE}"
echo "PASS: protected baseline checksums verified"
