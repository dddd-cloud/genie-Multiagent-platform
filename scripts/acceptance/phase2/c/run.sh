#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT_DIR}"

MVN_DOCKER="${ROOT_DIR}/scripts/acceptance/phase2/contract/mvn-docker.sh"
if [[ ! -f "${MVN_DOCKER}" ]]; then
  echo "Overall: BLOCKED (Docker Maven launcher missing)"
  exit 2
fi

# 第18节冻结清单：每个类对应文档列出的 Fake、Fixture 或验收证据。
# C-G01: GptProcessV1RegressionTest
# C-G02: Phase2RequestValidatorTest, Phase2ConversationLifecycleTest
# C-G03..C-G04: DirectReactAdapterTest, DirectPlanSolveAdapterTest, RouterFallbackTest
# C-G05..C-G07: OrchestrationValidatorTest, SerialMaxConcurrencyTest, InputRefsTransferTest,
#               StepFailureSkipTest, ReplanLimitTest, ResultReuseSignatureTest
# C-G08: SummaryFallbackTest
# C-G09..C-G10: Phase2TerminalRaceTest, OrchestrationSnapshotPrunerTest, FinalAnswerPersistenceTest
# C-G11..C-G12: ConfiguredReactAgentFactoryTest, AgentTaskResultParserTest,
#               ConfiguredAgentTestControllerTest, Phase2FakePortContractTest
TESTS=(
  Phase2RequestValidatorTest
  GptProcessV1RegressionTest
  Phase2ConversationLifecycleTest
  DirectReactAdapterTest
  DirectPlanSolveAdapterTest
  RouterFallbackTest
  OrchestrationValidatorTest
  SerialMaxConcurrencyTest
  InputRefsTransferTest
  StepFailureSkipTest
  ReplanLimitTest
  ResultReuseSignatureTest
  ConfiguredReactAgentFactoryTest
  AgentTaskResultParserTest
  SummaryFallbackTest
  Phase2TerminalRaceTest
  OrchestrationSnapshotPrunerTest
  FinalAnswerPersistenceTest
  ConfiguredAgentTestControllerTest
  Phase2FakePortContractTest
)

# 未传参执行第18节全量冻结清单；单独脚本可传逗号分隔的目标类。
if [[ "$#" -gt 1 ]]; then
  echo "Usage: $0 [TestClass[,TestClass...]]" >&2
  exit 2
fi
if [[ "$#" -eq 1 ]]; then
  TESTS_CSV="$1"
  IFS=',' read -r -a TESTS <<< "${TESTS_CSV}"
fi

echo "==> Phase2-C independent acceptance (C-G01..C-G12)"
echo "==> Docker Maven tests: ${TESTS[*]}"

bash "${MVN_DOCKER}" "-Djacoco.skip=true" "-Dtest=$(IFS=,; echo "${TESTS[*]}")" test

echo "Overall: PASS"
