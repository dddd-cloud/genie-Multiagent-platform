#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/../lib.sh"
run_scenario "event_trace_v2" "OrchestrationEventV2Test,OrchestrationTraceV2SubTaskTest,OrchestrationEventMapperTest"
