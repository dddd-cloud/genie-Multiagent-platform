#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
source "$ROOT/scripts/acceptance/phase2/b/_common.sh"
run_maven_tests "RuntimeToolCollectionPortTest,RuntimeToolCollectionPortContractTest"
printf '{"name":"runtime_tool_collection_fake","result":"PASS"}\n'
