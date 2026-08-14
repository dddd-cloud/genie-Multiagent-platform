package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.platform.agentbridge.CancellableAgentCall;
import com.jd.genie.platform.agentbridge.ConversationStreamObserver;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;

/**
 * 执行 Main/DIRECT fallback 的接口，供编排服务在步骤重试失败后调用。
 * 实现需对接现有的 DIRECT 执行路径（multiAgentService.searchForAgentRequest）。
 */
public interface DirectFallbackExecutor {
    /**
     * 使用非编排 DIRECT 能力执行指定目标。
     *
     * @param objective 当前步骤目标
     * @param observer 流观察器
     * @param cancellableCall 可取消调用
     * @return fallback 执行结果
     */
    AgentTaskResult executeFallback(
            String objective,
            ConversationStreamObserver observer,
            CancellableAgentCall cancellableCall
    );
}
