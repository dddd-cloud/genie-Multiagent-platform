#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
echo "[MVP-B] startup recovery for stale PENDING/STREAMING assistant messages" >&2
run_maven_gate "stale_message_recovery" "ConversationRecoveryServiceTest"