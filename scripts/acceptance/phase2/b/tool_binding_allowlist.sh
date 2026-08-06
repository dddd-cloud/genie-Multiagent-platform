#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
source "$ROOT/scripts/acceptance/phase2/b/_common.sh"
run_maven_tests "ToolBindingAtomicReplaceTest,ToolBindingIsolationTest,ToolBindingPortContractTest"
printf '{"name":"tool_binding_allowlist","result":"PASS"}\n'
