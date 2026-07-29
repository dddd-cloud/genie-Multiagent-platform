#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
echo "[MVP-B] assistant message state machine and ownership guards" >&2
run_maven_gate "message_state_machine" "ConversationMessageStateMachineTest"