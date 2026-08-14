#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/../lib.sh"
run_scenario "serial_top_level" "TopLevelSerialInvariantTest,SerialMaxConcurrencyTest,SerialOrchestrationServiceTest,InputRefsTransferTest,ResultReuseSignatureTest,StepFailureSkipTest,OrchestrationValidatorTest,OrchestrationPlanParserTest,OpenAiOrchestrationModelPortTest"
