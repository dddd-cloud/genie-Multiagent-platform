#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/../lib.sh"
run_scenario "auto_routes" "AutoRouteTest,RouterFallbackTest,Phase2GptProcessEntryTest,Phase2ConversationLifecycleTest,Phase2RequestValidatorTest"
