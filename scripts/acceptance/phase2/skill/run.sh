#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
export SKILL_ACCEPTANCE_TMP="$TMP_DIR"
source "$ROOT/scripts/acceptance/phase2/skill/_common.sh"

cases=(legacy_regression filesystem_package invalid_package_fail_closed path_symlink_security stable_sha256
  runtime_tool_build pyodide_signal_fake_printer bundle_ownership bundle_snapshot result_idempotency
  timeout_cancel native_python_not_required)
selector="LegacyCompatibleSkillRuntimeServiceTest,SkillPackageLoaderTest,SkillPackageHasherTest,PyodidePackageSpecValidationTest,SkillRuntimeToolBuildTest,RuntimeNameCollisionTest,BrowserPyodideSkillToolTest,BrowserSkillExecutionCoordinatorTest,BuiltinSkillSampleTest,Phase2ErrorCodeContractTest"

printf '==> skill acceptance: running complete Maven suite\n' >&2
if ! run_case "skill_complete_suite" "$selector" >"$TMP_DIR/skill_complete_suite.json"; then
  printf '{"name":"phase2_skill_005_acceptance","result":"FAIL","failed":"skill_complete_suite"}\n'
  exit 1
fi
for name in "${cases[@]}"; do printf '==> skill acceptance: %s PASS\n' "$name" >&2; done
printf '==> skill acceptance: package_spec_and_builtin_sample PASS\n' >&2

printf '{"name":"phase2_skill_005_acceptance","result":"PASS","scripts":%d}\n' "${#cases[@]}"
