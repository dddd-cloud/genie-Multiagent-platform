package com.jd.genie.platform.phase2.runtime.orchestration;

import java.util.Map;

/**
 * Assembles the prompt handed to a sub Agent for one orchestration step or subtask.
 * Extracted from SerialOrchestrationService so the wording can be tested on its own.
 */
final class StepQueryBuilder {

    private StepQueryBuilder() {
    }

    static String build(
            String agentName,
            String agentDescription,
            String userQuery,
            String conversationHistory,
            String objective,
            String longTermMemory,
            Map<String, String> inputs
    ) {
        return build(agentName, agentDescription, userQuery, conversationHistory, objective, longTermMemory, inputs, false);
    }

    /**
     * @param finalAnswer true when no summarizer follows this step — the caller sends
     *                     this step's output to the user verbatim (the solo-agent seam).
     */
    static String build(
            String agentName,
            String agentDescription,
            String userQuery,
            String conversationHistory,
            String objective,
            String longTermMemory,
            Map<String, String> inputs,
            boolean finalAnswer
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是子 Agent「").append(agentName == null ? "" : agentName).append("」。\n");
        if (agentDescription != null && !agentDescription.isBlank()) {
            sb.append("你的角色设定：").append(agentDescription.trim()).append('\n');
        }
        sb.append("用户原问题（只用于限定主题，不要改题）：\n");
        sb.append(userQuery == null ? "" : userQuery.trim()).append('\n');
        if (conversationHistory != null && !conversationHistory.isBlank()) {
            sb.append("\n近期对话（用于理解指代，不是新题目）：\n");
            sb.append(conversationHistory.trim()).append('\n');
        }
        sb.append("\n步骤目标（这就是你要完成的全部工作）：\n").append(objective == null ? "" : objective).append('\n');
        if (inputs != null && !inputs.isEmpty()) {
            sb.append("\n可参考的前置步骤结果：\n");
            for (Map.Entry<String, String> entry : inputs.entrySet()) {
                sb.append("- ").append(entry.getKey()).append(": ")
                        .append(entry.getValue() == null ? "" : entry.getValue())
                        .append('\n');
            }
            sb.append("如果本步骤需要整合上述结果，请写成新的段落，不要原样复述其中某一条。\n");
        }
        UntrustedLocalContext.appendBlock(sb, longTermMemory);
        if (finalAnswer) {
            sb.append("""

                    输出要求：
                    - 你的回答会直接展示给用户，之后不再有其他环节做总结或改写：请直接、完整地回答用户原问题，写成正式的最终答复。
                    - 不要用"事实发现""过程产出"这类内部工作汇报式的标题或框架，就像正常对话一样自然作答。
                    - 证据不足时，坦诚说明缺口是什么、还需要什么信息，不要猜测填充。
                    """);
        } else {
            sb.append("""

                    输出要求：
                    - 只交付本步骤目标的结果：事实发现、证据、来源、过程产出，以及仍然存在的不确定性。
                    - 不要为用户原问题写整体结论或总括性回答，那一步由后续环节完成。
                    - 证据不足时，明确写出缺口是什么、还需要什么信息，不要猜测填充。
                    - 用你自己的视角和措辞表达，避免与其他步骤输出雷同。
                    """);
        }
        return sb.toString();
    }
}
