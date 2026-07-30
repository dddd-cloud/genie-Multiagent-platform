#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
echo "[MVP-B] tenant/owner isolation through the real SecurityFilterChain" >&2
run_maven_gate "conversation_isolation" "FullSecurityConversationIsolationIntegrationTest"