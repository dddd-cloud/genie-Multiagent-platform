#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}"

list_protected_files() {
  printf '%s\n' \
    genie-backend/src/main/java/com/jd/genie/controller/GenieController.java \
    genie-backend/src/main/java/com/jd/genie/agent/agent/ReactImplAgent.java \
    genie-backend/src/main/java/com/jd/genie/agent/agent/ReActAgent.java \
    genie-backend/src/main/java/com/jd/genie/agent/agent/AgentContext.java \
    genie-backend/src/main/java/com/jd/genie/agent/tool/ToolCollection.java \
    genie-backend/src/main/java/com/jd/genie/agent/tool/mcp/McpTool.java \
    genie-backend/src/main/java/com/jd/genie/service/impl/ReactHandlerImpl.java \
    genie-backend/src/main/java/com/jd/genie/service/impl/PlanSolveHandlerImpl.java \
    genie-backend/src/main/java/com/jd/genie/platform/contract/ConversationExecutionPort.java \
    genie-backend/src/main/java/com/jd/genie/platform/conversation/service/ConversationExecutionService.java \
    genie-backend/src/main/java/com/jd/genie/platform/agentbridge/ConversationStreamObserver.java \
    genie-backend/src/main/java/com/jd/genie/platform/agentbridge/StreamPersistenceObserver.java \
    genie-backend/src/main/java/com/jd/genie/platform/agentbridge/StreamSnapshotBuffer.java \
    genie-backend/src/main/java/com/jd/genie/platform/agentbridge/FinalAnswerExtractor.java \
    genie-backend/src/main/resources/db/migration/V001__legacy_schema.sql \
    genie-backend/src/main/resources/db/migration/V002__identity_and_session.sql \
    genie-backend/src/main/resources/db/migration/V003__conversation.sql \
    ui/src/utils/querySSE.ts \
    genie-backend/src/main/resources/db/migration/V004__agent_and_skill.sql \
    genie-backend/src/main/resources/db/migration/V005__mcp_and_tool_binding.sql \
    genie-backend/src/main/java/com/jd/genie/platform/phase2/tooling/RuntimeToolCollectionService.java \
    genie-backend/src/main/java/com/jd/genie/platform/phase2/tooling/AuthorizedToolCollection.java \
    genie-backend/src/main/java/com/jd/genie/platform/phase2contract/dto/OrchestrationEvent.java \
    genie-backend/src/main/java/com/jd/genie/platform/phase2contract/port/RuntimeToolCollectionPort.java \
    genie-backend/src/main/java/com/jd/genie/platform/phase2contract/port/AgentSkillBindingPort.java \
    genie-backend/src/main/java/com/jd/genie/platform/phase2contract/port/SkillRuntimePort.java \
    genie-backend/src/main/java/com/jd/genie/platform/contract/MvpErrorCode.java \
    genie-backend/src/main/java/com/jd/genie/platform/phase2contract/error/Phase2ErrorHttpStatus.java
}

if [[ "${1:-}" == "--list" ]]; then
  list_protected_files
  exit 0
fi

if [[ "${ALLOW_C0_BASELINE_REGENERATION:-}" != "1" ]]; then
  echo "BLOCKED: set ALLOW_C0_BASELINE_REGENERATION=1 to regenerate"
  exit 2
fi

if ! command -v git >/dev/null 2>&1; then
  echo "BLOCKED: git is required"
  exit 2
fi

if ! command -v sha256sum >/dev/null 2>&1; then
  echo "BLOCKED: sha256sum is required"
  exit 2
fi

mapfile -t files < <(list_protected_files)

git diff --quiet -- "${files[@]}" || {
  echo "FAIL: protected files have unstaged changes"
  exit 1
}

git diff --cached --quiet -- "${files[@]}" || {
  echo "FAIL: protected files have staged changes"
  exit 1
}

tmp_file="$(mktemp)"
trap 'rm -f "${tmp_file}"' EXIT

for file in "${files[@]}"; do
  git cat-file -e "HEAD:${file}" 2>/dev/null || {
    echo "FAIL: protected path missing from HEAD: ${file}"
    exit 1
  }

  digest="$(
    git cat-file blob "$(git rev-parse "HEAD:${file}")" |
      sha256sum |
      awk '{print $1}'
  )"

  printf '%s  %s\n' "${digest}" "${file}" >> "${tmp_file}"
done

mkdir -p docs/mvp-contract/phase2
mv "${tmp_file}" docs/mvp-contract/phase2/protected-baseline.sha256
trap - EXIT

echo "Generated docs/mvp-contract/phase2/protected-baseline.sha256 from Git HEAD blobs"
