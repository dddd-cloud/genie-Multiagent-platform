#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_common.sh"
if run_maven_tests "Phase2BMySqlMigrationTest"; then json_result migration_v005 PASS; else json_result migration_v005 FAIL; exit 1; fi
