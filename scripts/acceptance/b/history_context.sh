#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
echo "[MVP-B] completed-history context window limits" >&2
run_maven_gate "history_context" "ConversationHistoryServiceTest"