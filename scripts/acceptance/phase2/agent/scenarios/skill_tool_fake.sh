#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/../lib.sh"
run_scenario "skill_tool_fake" "SkillRuntimeToolInjectionTest,StructuredAgentResultStrictnessTest,AgentTaskResultParserTest,ConfiguredAgentExecutorTest,ConfiguredReactAgentFactoryTest,AgentTestServiceTest"
