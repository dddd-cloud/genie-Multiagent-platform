import { describe, expect, it } from 'vitest';
import type { OrchestrationEvent } from '@/contracts';
import {
  createInitialOrchestrationState,
  reduceOrchestrationEvent,
} from '../orchestrationReducer';

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

describe('EventV2ParallelReducerTest', () => {
  it('allows PARALLEL_STARTED + multiple SUBTASK_STARTED RUNNING under one step', () => {
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
            objective: 'Parallel research',
            inputRefs: [],
            mode: 'PARALLEL_AGENTS',
            subTasks: [
              {
                subTaskId: 'st-a',
                agentId: 'agent-a',
                agentName: 'Agent A',
                objective: 'Research A',
              },
              {
                subTaskId: 'st-b',
                agentId: 'agent-b',
                agentName: 'Agent B',
                objective: 'Research B',
              },
            ],
          },
        ],
      }),
    );

    expect(state.schemaVersion).toBe(2);
    expect(state.attempts[1].steps.s1.stepMode).toBe('PARALLEL_AGENTS');
    expect(state.attempts[1].steps.s1.subTasks['st-a'].status).toBe('PLANNED');
    expect(state.attempts[1].steps.s1.subTasks['st-b'].status).toBe('PLANNED');

    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e3',
        sequence: 3,
        eventType: 'PARALLEL_STARTED',
        stepId: 's1',
        stepMode: 'PARALLEL_AGENTS',
      }),
    );
    expect(state.attempts[1].steps.s1.status).toBe('RUNNING');

    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e4',
        sequence: 4,
        eventType: 'SUBTASK_STARTED',
        stepId: 's1',
        subTaskId: 'st-a',
        agentId: 'agent-a',
        agentName: 'Agent A',
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e5',
        sequence: 5,
        eventType: 'SUBTASK_STARTED',
        stepId: 's1',
        subTaskId: 'st-b',
        agentId: 'agent-b',
        agentName: 'Agent B',
      }),
    );

    const step = state.attempts[1].steps.s1;
    expect(step.status).toBe('RUNNING');
    expect(step.subTasks['st-a'].status).toBe('RUNNING');
    expect(step.subTasks['st-b'].status).toBe('RUNNING');
    expect(
      Object.values(step.subTasks).filter((s) => s.status === 'RUNNING').length,
    ).toBe(2);
  });
});
