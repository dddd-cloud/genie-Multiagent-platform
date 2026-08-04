#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_common.sh"
if run_maven_tests "McpUrlPolicyTest,DnsAddressPolicyTest,McpServiceUrlGuardTest"; then json_result ssrf_rebinding_rejection PASS; else json_result ssrf_rebinding_rejection FAIL; exit 1; fi
