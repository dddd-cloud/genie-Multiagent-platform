#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/../lib.sh"
run_scenario "cancel_single_terminal" "ParallelCancelTest,TerminalRaceTest,SingleFinalResponseTest,Phase2TerminalRaceTest"
