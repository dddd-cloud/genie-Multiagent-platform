import { describe, expect, it } from 'vitest';
import type { OrchestrationEvent } from '@/contracts';
import {
  createInitialOrchestrationState,
  reduceOrchestrationEvent,
  reduceOrchestrationTrace,
} from '../orchestrationReducer';
import type { OrchestrationTrace } from '../parseOrchestrationTrace';

function event(
  partial: Partial<OrchestrationEvent> &
    Pick<OrchestrationEvent, 'eventId' | 'sequence' | 'eventType'>,
): OrchestrationEvent {
  return {
    schemaVersion: 2,
    requestId: 'req-1',
    runId: 'run-1',
    attemptNo: 1,
    stepId: null,
    agentId: null,
    agentName: null,
    route: null,
    reasonCode: null,
    errorCode: null,
    steps: [],
    completionStatus: null,
    subTaskId: null,
    stepMode: null,
    retryNo: null,
    ...partial,
  };
}

function trace(
  partial: Partial<OrchestrationTrace> &
    Pick<OrchestrationTrace, 'sequence' | 'scope' | 'kind' | 'text'>,
): OrchestrationTrace {
  return {
    schemaVersion: 2,
    requestId: 'req-1',
    runId: 'run-1',
    attemptNo: 1,
    stepId: 's1',
    agentId: null,
    agentName: null,
    append: false,
    truncated: false,
    subTaskId: null,
    retryNo: null,
    ...partial,
  };
}

describe('TraceV2SubTaskTest', () => {
  it('routes SUBTASK traces onto the matching subTaskId lines', () => {
    let state = createInitialOrchestrationState();
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e1',
        sequence: 1,
        eventType: 'ROUTE_SELECTED',
        attemptNo: null,
        route: 'ORCHESTRATED',
        reasonCode: 'MULTI_AGENT',
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e2',
        sequence: 2,
        eventType: 'PLAN_CREATED',
        steps: [
          {
            stepId: 's1',
            agentId: null,
            agentName: null,
            objective: 'Parallel',
            inputRefs: [],
            mode: 'PARALLEL_AGENTS',
            subTasks: [
              {
                subTaskId: 'st-a',
                agentId: 'agent-a',
                agentName: 'A',
                objective: 'A work',
              },
              {
                subTaskId: 'st-b',
                agentId: 'agent-b',
                agentName: 'B',
                objective: 'B work',
              },
            ],
          },
        ],
      }),
    );

    state = reduceOrchestrationTrace(
      state,
      trace({
        sequence: 3,
        scope: 'SUBTASK',
        subTaskId: 'st-a',
        agentId: 'agent-a',
        agentName: 'A',
        kind: 'THOUGHT',
        text: 'thinking A',
      }),
    );
    state = reduceOrchestrationTrace(
      state,
      trace({
        sequence: 4,
        scope: 'SUBTASK',
        subTaskId: 'st-b',
        agentId: 'agent-b',
        agentName: 'B',
        kind: 'OUTPUT',
        text: 'output B',
      }),
    );

    const step = state.attempts[1].steps.s1;
    expect(step.lines).toEqual([]);
    expect(step.subTasks['st-a'].lines.map((l) => l.text)).toEqual([
      'thinking A',
    ]);
    expect(step.subTasks['st-b'].lines.map((l) => l.text)).toEqual([
      'output B',
    ]);
    expect(state.lastTraceSequence).toBe(4);
    expect(step.subTasks['st-a'].status).toBe('RUNNING');
    expect(step.subTasks['st-b'].status).toBe('RUNNING');
  });

  it('keeps the planned Chinese name when a later trace sends a UUID', () => {
    let state = createInitialOrchestrationState();
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e1',
        sequence: 1,
        eventType: 'ROUTE_SELECTED',
        attemptNo: null,
        route: 'ORCHESTRATED',
        reasonCode: 'MULTI_AGENT',
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e2',
        sequence: 2,
        eventType: 'PLAN_CREATED',
        steps: [
          {
            stepId: 's1',
            agentId: null,
            agentName: null,
            objective: 'Parallel',
            inputRefs: [],
            mode: 'PARALLEL_AGENTS',
            subTasks: [
              {
                subTaskId: 'st-back',
                agentId: '4ec620f8-9449-4bc1-9f4c-44f423d56914',
                agentName: '代码团队·后端',
                objective: '写接口',
              },
              {
                subTaskId: 'st-front',
                agentId: 'front-id',
                agentName: '代码团队·前端',
                objective: '写页面',
              },
            ],
          },
        ],
      }),
    );

    state = reduceOrchestrationTrace(
      state,
      trace({
        sequence: 3,
        scope: 'SUBTASK',
        subTaskId: 'st-back',
        agentId: '4ec620f8-9449-4bc1-9f4c-44f423d56914',
        agentName: '4ec620f8-9449-4bc1-9f4c-44f423d56914',
        kind: 'THOUGHT',
        text: '先写会话接口。',
      }),
    );

    const back = state.attempts[1].steps.s1.subTasks['st-back'];
    expect(back.agentName).toBe('代码团队·后端');
    expect(back.status).toBe('RUNNING');
  });

  it('reroutes leftover parent STEP traces onto the matching subtask', () => {
    let state = createInitialOrchestrationState();
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e1',
        sequence: 1,
        eventType: 'ROUTE_SELECTED',
        attemptNo: null,
        route: 'ORCHESTRATED',
        reasonCode: 'MULTI_AGENT',
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e2',
        sequence: 2,
        eventType: 'PLAN_CREATED',
        steps: [
          {
            stepId: 's1',
            agentId: null,
            agentName: null,
            objective: 'Parallel',
            inputRefs: [],
            mode: 'PARALLEL_AGENTS',
            subTasks: [
              {
                subTaskId: 'st-a',
                agentId: 'agent-a',
                agentName: '前端',
                objective: 'A work',
              },
              {
                subTaskId: 'st-b',
                agentId: 'agent-b',
                agentName: '后端',
                objective: 'B work',
              },
            ],
          },
        ],
      }),
    );

    state = reduceOrchestrationTrace(
      state,
      trace({
        sequence: 3,
        scope: 'STEP',
        stepId: 's1',
        agentId: 'agent-a',
        agentName: '前端',
        kind: 'THOUGHT',
        text: 'parent leak A',
      }),
    );
    state = reduceOrchestrationTrace(
      state,
      trace({
        sequence: 4,
        scope: 'STEP',
        stepId: 's1',
        agentId: null,
        agentName: '前端',
        kind: 'THOUGHT',
        text: 'unroutable ghost',
      }),
    );

    const step = state.attempts[1].steps.s1;
    expect(step.lines).toEqual([]);
    expect(step.agentName).toBe('');
    expect(step.subTasks['st-a'].lines.map((l) => l.text)).toEqual([
      'parent leak A',
    ]);
    expect(step.subTasks['st-b'].lines).toEqual([]);
    expect(state.lastTraceSequence).toBe(4);
  });
});
