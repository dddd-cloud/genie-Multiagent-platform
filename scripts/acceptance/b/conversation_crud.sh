#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
echo "[MVP-B] CRUD, pagination, preview, rename, and soft delete via real MySQL" >&2
run_maven_gate "conversation_crud" "ConversationCrudApiTest"