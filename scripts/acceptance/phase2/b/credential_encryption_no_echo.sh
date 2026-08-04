#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_common.sh"
if run_maven_tests "CredentialEnvelopeServiceTest"; then json_result credential_encryption_no_echo PASS; else json_result credential_encryption_no_echo FAIL; exit 1; fi
