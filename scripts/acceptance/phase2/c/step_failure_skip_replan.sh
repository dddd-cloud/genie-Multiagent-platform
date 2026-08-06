#!/usr/bin/env bash
set -euo pipefail
bash "$(dirname "$0")/run.sh" StepFailureSkipTest,ReplanLimitTest,ResultReuseSignatureTest
