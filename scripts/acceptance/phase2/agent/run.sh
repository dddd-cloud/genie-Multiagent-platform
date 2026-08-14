#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${ROOT_DIR}"

# 阶段6（文档第10节）冻结的 Agent 独立验收固定白名单。
# 不使用 glob 扫描任意脚本；缺失、非预期输出或失败均导致总入口失败。
SCENARIOS=(
  v1_regression
  direct_modes
  auto_routes
  serial_top_level
  parallel_two_agents
  parallel_same_agent
  parallel_wait_all
  selective_retry
  main_fallback
  degraded_partial
  skill_tool_fake
  browser_skill_signal
  event_trace_v2
  snapshot_v1
  cancel_single_terminal
)

JSON_LINE='^\{"name":"[a-z0-9_]+","result":"(PASS|FAIL)"\}$'

failures=0
for scenario in "${SCENARIOS[@]}"; do
  script="${SCRIPT_DIR}/scenarios/${scenario}.sh"
  if [[ ! -f "${script}" ]]; then
    echo "BLOCKED: missing scenario script ${script}"
    exit 2
  fi
  if output="$(bash "${script}")"; then
    if ! printf '%s\n' "${output}" | grep -qE "${JSON_LINE}"; then
      echo "INVALID OUTPUT: scenario ${scenario} -> ${output}"
      failures=1
      continue
    fi
    echo "${output}"
  else
    echo "${output}"
    echo "FAIL: scenario ${scenario} exited non-zero"
    failures=1
  fi
done

if [[ ${failures} -ne 0 ]]; then
  echo "Overall: FAIL"
  exit 1
fi

echo "Overall: PASS"

