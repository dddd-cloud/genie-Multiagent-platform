#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
cd "$ROOT/genie-backend"
exec mvn -Dtest=Phase2BMySqlMigrationTest,CredentialEnvelopeServiceTest,McpServerOwnershipTest,McpServerVersionConflictTest,McpUrlPolicyTest,DnsAddressPolicyTest,McpDiscoveryTransactionTest,McpToolAvailabilityStateTest,McpRuntimeNameCollisionTest,GenieClientInternalAuthTest,GenieClientRedactionTest test
