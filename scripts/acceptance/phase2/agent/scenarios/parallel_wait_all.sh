#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/../lib.sh"
run_scenario "parallel_wait_all" "ParallelWaitAllTest,SubTaskInputIsolationTest"
