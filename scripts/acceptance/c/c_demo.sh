#!/usr/bin/env bash
# C 模块分阶段功能展示
# 用途：在 Maven Docker 容器内按阶段运行 C 私有测试，不依赖外部服务，验证各阶段核心逻辑正确。
# 用法（宿主 PowerShell）：
#   docker run --rm --network none \
#     -v "g:/joyagent-secondary-dev:/workspace:ro" \
#     -v mvp-c-maven-cache:/root/.m2 \
#     maven:3.9.9-eclipse-temurin-17 \
#     bash /workspace/scripts/acceptance/c/c_demo.sh
set -euo pipefail

ROOT=/workspace
BUILD=/build
DOCS_DEST=/docs
REP="${BUILD}/target/surefire-reports"
PKG="com.jd.genie.platform.agentbridge"
PKG_ACC="${PKG}.acceptance"

hr() { printf '%0.s─' {1..70}; printf '\n'; }

phase() {
  local label="$1"; shift
  printf '\n'; hr
  printf '■ %s\n' "$label"
  hr
  for cls in "$@"; do
    local f="${REP}/${PKG}.${cls}.txt"
    [[ -f "$f" ]] || f="${REP}/${PKG_ACC}.${cls}.txt"
    if [[ -f "$f" ]]; then
      local summary
      summary=$(grep -m1 "^Tests run:" "$f" 2>/dev/null || echo "(no summary)")
      local failures errors
      failures=$(echo "$summary" | grep -oP 'Failures: \K[0-9]+' || echo "?")
      errors=$(echo   "$summary" | grep -oP 'Errors: \K[0-9]+'   || echo "?")
      local status="PASS"
      [[ "$failures" == "0" && "$errors" == "0" ]] || status="FAIL"
      printf '  [%s] %-55s %s\n' "$status" "$cls" "$summary"
      # 列出测试方法名（一行一个）
      grep -oP '(?<=testcase name=")[^"]+' \
        "${f%.txt}.xml" 2>/dev/null | sort | \
        sed 's/^/        · /' || true
    else
      printf '  [MISS] %-55s (report not found)\n' "$cls"
    fi
  done
}

# ── 准备构建目录 ────────────────────────────────────────────
printf 'Copying sources...\n'
cp -a "${ROOT}/genie-backend" "${BUILD}"
mkdir -p "${DOCS_DEST}"
cp -a "${ROOT}/docs/mvp-contract" "${DOCS_DEST}/"

# ── 编译 + 运行所有 C 模块测试（一次性）──────────────────────
# 注意：显式列出类名而不是用包名通配符（如 agentbridge.*Test）。
# 该通配符在部分 Surefire 版本下存在非确定性行为，偶发跨包吞入
# com.jd.genie.platform.contract 下的类，导致证据不可复现。
CLASSES="AgentHistoryMessageMapperTest,StreamSnapshotBufferTest,SnapshotPrunerTest,\
FinalAnswerExtractorTest,SnapshotFixtureTest,ConversationStreamObserverLifecycleTest,\
ConversationStreamObserverFailureTest,ConversationStreamObserverErrorBoundaryTest,\
ConversationStreamObserverPersistenceBoundaryTest,ConversationStreamObserverConcurrencyTest,\
StreamPersistenceObserverTest,SseUtilLifecycleTest,AgentExecutionRequestFactoryTest,\
GptProcessServiceOrchestrationTest,MultiAgentServiceSuccessTest,MultiAgentServiceFailureTest,\
MultiAgentServiceCancellationTest,CancellableAgentCallTest,AgentHistoryMemoryBridgeTest,\
FakeAgentAcceptanceFilterTest,FakeAgentAcceptanceProfileTest,CModuleCapabilityWalkthroughTest"

printf 'Building and running all C module tests (offline, explicit class list)...\n\n'
cd "${BUILD}"
mvn -o -Dstyle.color=never test -Dtest="${CLASSES}" 2>&1 | \
  grep -E '^\[ERROR\]|^\[INFO\] Running|Tests run:' || true

# ── 按阶段输出报告 ──────────────────────────────────────────
printf '\n\n'; hr
printf '  MVP-C 模块：分阶段功能验证报告\n'
hr

phase \
  "阶段 1 ─ 纯数据规则组件（历史映射 / Snapshot 缓冲 / 裁剪 / 最终回答提取）" \
  AgentHistoryMessageMapperTest \
  StreamSnapshotBufferTest \
  SnapshotPrunerTest \
  FinalAnswerExtractorTest \
  SnapshotFixtureTest

phase \
  "阶段 2 ─ 流状态与终态协调（单向终态 / Fail/Complete/Interrupt 路径）" \
  ConversationStreamObserverLifecycleTest \
  ConversationStreamObserverFailureTest \
  ConversationStreamObserverErrorBoundaryTest \
  ConversationStreamObserverPersistenceBoundaryTest \
  ConversationStreamObserverConcurrencyTest \
  StreamPersistenceObserverTest \
  SseUtilLifecycleTest

phase \
  "阶段 3 ─ 外层请求编排接入（ID语义 / 用户隔离 / Fake Port 状态顺序）" \
  AgentExecutionRequestFactoryTest \
  GptProcessServiceOrchestrationTest

phase \
  "阶段 4 ─ 内部 Agent SSE 适配（成功/HTTP 500/断流/格式错误/无最终事件）" \
  MultiAgentServiceSuccessTest \
  MultiAgentServiceFailureTest \
  MultiAgentServiceCancellationTest \
  CancellableAgentCallTest

phase \
  "阶段 5 ─ ReAct/Plan 历史上下文桥接（角色映射 / Memory 注入边界）" \
  AgentHistoryMemoryBridgeTest

phase \
  "阶段 6 ─ Fake Agent 验收能力（Profile 隔离 / 7 种模式 / 生产无 Bean）" \
  FakeAgentAcceptanceFilterTest \
  FakeAgentAcceptanceProfileTest

# ── 综合演示：跨阶段真实输入/输出 ────────────────────────────
printf '\n'; hr
printf '■ 综合演示 ─ 用真实 C 类 + 冻结 fixture 演示每个核心职责的输入→输出\n'
hr
WALKTHROUGH="${REP}/${PKG}.CModuleCapabilityWalkthroughTest.txt"
if [[ -f "$WALKTHROUGH" ]]; then
  cat "$WALKTHROUGH"
else
  printf '  [MISS] CModuleCapabilityWalkthroughTest report not found\n'
fi

# ── 总计 ──────────────────────────────────────────────────
printf '\n'; hr
TOTAL=$(grep -h "^Tests run:" "${REP}"/*.txt 2>/dev/null | \
  awk -F'[,: ]+' '{t+=$3; f+=$5; e+=$7; s+=$9} END{printf "Tests: %d  Failures: %d  Errors: %d  Skipped: %d\n", t,f,e,s}')
printf '  汇总  %s\n' "$TOTAL"
hr
