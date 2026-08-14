#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/../lib.sh"
run_scenario "snapshot_v1" "SnapshotV1CompatibilityTest,SnapshotPrunerTest,OrchestrationSnapshotPrunerTest,FinalAnswerPersistenceTest"
