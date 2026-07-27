#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/c_acceptance.sh"
run_c_acceptance "fake_agent_disconnect" "$0" "$@"
