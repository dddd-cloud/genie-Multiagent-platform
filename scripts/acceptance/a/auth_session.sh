#!/usr/bin/env bash
exec "$(dirname "$0")/_run_maven_acceptance.sh" "auth_session.sh" "SecurityCsrfIntegrationTest,SessionRestartPersistenceIntegrationTest,GenieApplicationMvpAcceptanceIntegrationTest"
