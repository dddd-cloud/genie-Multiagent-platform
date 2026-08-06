#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"
cd "$REPO_ROOT"

echo "==> Phase2-A final acceptance"
if ! resolve_tooling; then
  echo "Overall: BLOCKED"
  exit 2
fi
write_tool_versions
cat "$TOOL_VERSION_FILE"

G1_START=$(start_ms)
echo "==> 1/12 Repository and boundary audit"
CURRENT_BRANCH="$(git branch --show-current)"
git rev-parse --verify "$A_BASELINE_COMMIT" >/dev/null
if [[ "$CURRENT_BRANCH" == "feature/phase2-a-configuration-memory" ]]; then
  verify_boundary || fail_gate "01" "Repository and boundary audit" "git diff --name-only $A_BASELINE_COMMIT..HEAD" "branch diff exceeds PHASE2-A whitelist" "$(elapsed_ms "$G1_START")"
  verify_forbidden_paths || fail_gate "01" "Repository and boundary audit" "boundary forbidden path scan" "forbidden file path changed" "$(elapsed_ms "$G1_START")"
elif [[ "$CURRENT_BRANCH" == "data_agent" ]]; then
  echo "Integrate mode on data_agent — skip A-only boundary whitelist"
else
  fail_gate "01" "Repository and boundary audit" "git branch --show-current" "wrong branch (expected feature/phase2-a-configuration-memory or data_agent)" "$(elapsed_ms "$G1_START")"
fi
git diff --check
record_gate "01" "Repository and boundary audit" "PASS" "git diff --check and boundary scan" 0 0 0 0 "$(elapsed_ms "$G1_START")" "branch=${CURRENT_BRANCH}; boundary checks applied when on feature branch"

run_maven_gate "02" "Migration and schema" "Phase2AMySqlMigrationTest"
run_maven_gate "03" "Persistence and ownership" "AgentDefinitionMapperMySqlTest,AgentSkillBindingMapperMySqlTest,SkillDefinitionMapperMySqlTest,AgentOwnershipIsolationTest,SkillOwnershipIsolationTest,AgentVersionConflictTest,SkillVersionConflictTest"
run_maven_gate "04" "Lifecycle and transaction rollback" "AgentDefinitionServiceTest,SkillDefinitionServiceTest,AgentStateTransitionTest,SkillStateTransitionTest,AgentOnlineValidationTest,AgentOnlinePromptRevalidationTest,SkillInUseDeleteTest,SkillOrderingTransactionTest,ToolBindingRollbackTest"
run_maven_gate "05" "Prompt compiler and model catalog" "AgentPromptCompilerTest,AgentPromptIntegrationTest,AgentRawPromptPersistenceTest,PromptPreviewServiceTest,PromptCodePointLengthTest,PromptFallbackConsistencyTest,PromptForbiddenDefaultScanTest,PromptPlaceholderValidationTest,PromptYamlLoadTest,ModelCatalogServiceTest,ModelSecretProjectionTest,AgentModelValidationTest"
run_maven_gate "06" "Runtime agent catalog" "AgentRuntimeCatalogCapabilityTest,AgentRuntimeCatalogIsolationTest,AgentRuntimeCatalogMySqlTest,AgentRuntimeCatalogPortTest,AgentRuntimeProfileFactoryTest,AgentRuntimeProfileImmutabilityTest"
run_maven_gate "07" "Memory and summary analysis" "ConversationSummaryAnalysisServiceTest,ConversationSummaryValidatorTest,MemoryAnalysisNoPersistenceTest,MemoryAnalysisServiceTest,MemoryInputValidatorTest,MemoryPatchValidatorTest"
run_maven_gate "08" "REST API contract and security" "Phase2AAgentApiContractTest,Phase2ASkillApiContractTest,Phase2AModelApiContractTest,Phase2APromptPreviewApiContractTest,Phase2AMemoryApiContractTest,Phase2ASummaryApiContractTest,Phase2AApiErrorContractTest,Phase2AApiSecurityTest,Phase2AAgentApiMySqlTest,Phase2ASkillApiMySqlTest,Phase2AApiOwnershipMySqlTest,Phase2AApiTransactionRollbackTest,Phase2SecurityIntegrationTest"

G9_START=$(start_ms)
echo "==> 9/12 Generic prompt regression"
verify_prompt_generalization_static || fail_gate "09" "Generic prompt regression" "static prompt scan" "default prompt specialization found" "$(elapsed_ms "$G9_START")"
run_maven_gate "09" "Generic prompt regression" "GenericPromptRegressionTest,V1PromptScenarioRegressionTest"

run_maven_gate "10" "V1 / agentbridge regression" "AgentHistoryMemoryBridgeTest,CModuleCapabilityWalkthroughTest,FinalAnswerExtractorTest,ReActPlanHandlerHistoryRegressionTest,SnapshotFixtureTest,ContractShapeTest,ContractSerializationTest,ContractFakeSupportTest,Phase2ContractShapeTest,Phase2ContractSerializationTest,Phase2ErrorCodeContractTest"

run_shell_gate "11" "C0 contract regression" bash scripts/acceptance/phase2/contract/run.sh
if ! grep -R -q 'Overall: PASS' "$LOG_DIR/gate-11-shell.log"; then
  fail_gate "11" "C0 contract regression" "bash scripts/acceptance/phase2/contract/run.sh" "C0 output did not contain Overall: PASS"
fi

G12_START=$(start_ms)
echo "==> 12/12 Evidence validation"
verify_secret_scan || fail_gate "12" "Evidence validation" "secret scan" "secret or boundary pattern found" "$(elapsed_ms "$G12_START")"
verify_evidence_files || fail_gate "12" "Evidence validation" "evidence file validation" "evidence missing or sensitive marker found" "$(elapsed_ms "$G12_START")"
git diff --check
record_gate "12" "Evidence validation" "PASS" "secret scan; evidence validation; git diff --check" 0 0 0 0 "$(elapsed_ms "$G12_START")" "evidence is present and sanitized"

echo "Evidence: $EVIDENCE_DIR"
echo "Overall: PASS"