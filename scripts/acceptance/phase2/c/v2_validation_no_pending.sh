#!/usr/bin/env bash
set -euo pipefail
bash "$(dirname "$0")/run.sh" Phase2RequestValidatorTest,Phase2ConversationLifecycleTest
