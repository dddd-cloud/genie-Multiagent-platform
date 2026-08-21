import { describe, expect, it } from 'vitest';
import {
  humanErrorMessage,
  humanRouteSubtitle,
  looksLikeInternalStatus,
  looksLikeProtocolDump,
  displayAgentName,
} from '../orchestrationCopy';
import { createInitialOrchestrationState } from '../orchestrationReducer';

describe('orchestrationCopy', () => {
  it('turns routing enums into product language', () => {
    const state = {
      ...createInitialOrchestrationState(),
      route: 'ORCHESTRATED' as const,
      routeReasonCode: 'RESOURCE_CREATION_REQUEST',
    };
    expect(humanRouteSubtitle(state)).toBe(
      '已选择编排执行，正在创建所需资源',
    );
    expect(looksLikeProtocolDump('路由决策：ORCHESTRATED（RESOURCE_CREATION_REQUEST）')).toBe(
      true,
    );
    expect(looksLikeProtocolDump('- [step-1] 市场研究员：梳理市场规模')).toBe(
      true,
    );
    expect(looksLikeProtocolDump('正在根据用户问题汇总各专家结论…')).toBe(
      false,
    );
    expect(looksLikeProtocolDump('可正常接收并处理任务。","errorCode')).toBe(
      true,
    );
    expect(looksLikeProtocolDump('":null,"retryable":false}')).toBe(true);
    expect(looksLikeInternalStatus('开始执行：写贪吃蛇前端')).toBe(true);
    expect(looksLikeInternalStatus('已将「写页面」交给 前端')).toBe(true);
  });

  it('hides uuid agent ids and maps error codes', () => {
    expect(
      displayAgentName({
        agentName: '4ec620f8-9449-4bc1-9f4c-44f423d56914',
        agentId: '4ec620f8-9449-4bc1-9f4c-44f423d56914',
        fallback: '专家1',
      }),
    ).toBe('专家1');
    expect(humanErrorMessage('AGENT_INVALID_RESULT')).toContain('可用结论');
    expect(humanErrorMessage('TOOL_TIMEOUT')).toContain('超时');
  });
});
