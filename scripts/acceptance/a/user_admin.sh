#!/usr/bin/env bash
exec "$(dirname "$0")/_run_maven_acceptance.sh" "user_admin.sh" "UserPersistenceIntegrationTest,AdminUserManagementIntegrationTest,SessionRevocationIntegrationTest"
