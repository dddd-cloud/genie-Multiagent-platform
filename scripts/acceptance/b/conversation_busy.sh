#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
echo "[MVP-B] active assistant busy semantics and concurrent turn protection" >&2
run_maven_gate "conversation_busy" "ConversationExecutionServiceTest#activeAssistantBlocksDifferentRequestButTerminalAssistantDoesNot+concurrentDifferentRequestIdsAllowOneSuccessAndOneBusyWithoutDuplicateTurnOrPartialRows"