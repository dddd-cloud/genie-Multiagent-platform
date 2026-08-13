#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/../lib.sh"
run_scenario "v1_regression" "V1RegressionTest,GptProcessV1RegressionTest"
