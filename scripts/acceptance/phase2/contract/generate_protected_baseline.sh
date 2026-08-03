#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}"

files=(
  genie-backend/src/main/java/com/jd/genie/controller/GenieController.java
  genie-backend/src/main/java/com/jd/genie/agent/agent/ReactImplAgent.java
  genie-backend/src/main/java/com/jd/genie/agent/agent/ReActAgent.java
  genie-backend/src/main/java/com/jd/genie/agent/agent/AgentContext.java
  genie-backend/src/main/java/com/jd/genie/agent/tool/ToolCollection.java
  genie-backend/src/main/java/com/jd/genie/agent/tool/mcp/McpTool.java
  genie-backend/src/main/java/com/jd/genie/service/impl/ReactHandlerImpl.java
  genie-backend/src/main/java/com/jd/genie/service/impl/PlanSolveHandlerImpl.java
  genie-backend/src/main/java/com/jd/genie/platform/contract/ConversationExecutionPort.java
  genie-backend/src/main/java/com/jd/genie/platform/conversation/service/ConversationExecutionService.java
  genie-backend/src/main/java/com/jd/genie/platform/agentbridge/ConversationStreamObserver.java
  genie-backend/src/main/java/com/jd/genie/platform/agentbridge/StreamPersistenceObserver.java
  genie-backend/src/main/java/com/jd/genie/platform/agentbridge/StreamSnapshotBuffer.java
  genie-backend/src/main/java/com/jd/genie/platform/agentbridge/FinalAnswerExtractor.java
  genie-backend/src/main/resources/db/migration/V001__legacy_schema.sql
  genie-backend/src/main/resources/db/migration/V002__identity_and_session.sql
  genie-backend/src/main/resources/db/migration/V003__conversation.sql
  ui/src/utils/querySSE.ts
)

mkdir -p docs/mvp-contract/phase2
: > docs/mvp-contract/phase2/protected-baseline.sha256
for f in "${files[@]}"; do
  sha256sum "$f" >> docs/mvp-contract/phase2/protected-baseline.sha256
done

# Ensure LF-only for portable sha256sum --check
sed -i 's/\r$//' docs/mvp-contract/phase2/protected-baseline.sha256
sha256sum --check docs/mvp-contract/phase2/protected-baseline.sha256
echo "Generated docs/mvp-contract/phase2/protected-baseline.sha256"
