#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_common.sh"
if run_python_tests; then json_result genie_client_internal_auth PASS; else json_result genie_client_internal_auth FAIL; exit 1; fi
