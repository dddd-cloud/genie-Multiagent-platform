#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/c_acceptance.sh"
run_c_acceptance "history_context" "$0" "$@"
