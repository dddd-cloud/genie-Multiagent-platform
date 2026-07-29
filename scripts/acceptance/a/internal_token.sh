#!/usr/bin/env bash
exec "$(dirname "$0")/_run_maven_acceptance.sh" "internal_token.sh" "InternalAgentSecurityIntegrationTest,InternalAgentServletErrorDispatchIntegrationTest"
