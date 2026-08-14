#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/../lib.sh"
run_scenario "main_fallback" "MainFallbackTest,FallbackNoRecursionTest,MainOnlyNoFallbackTest"
