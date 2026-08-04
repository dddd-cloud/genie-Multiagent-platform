#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_common.sh"
if run_maven_tests "McpDiscoveryTransactionTest,McpToolAvailabilityStateTest"; then json_result discovery_atomic_availability PASS; else json_result discovery_atomic_availability FAIL; exit 1; fi
