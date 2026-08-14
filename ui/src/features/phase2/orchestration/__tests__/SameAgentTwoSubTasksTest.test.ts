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

describe('SameAgentTwoSubTasksTest', () => {
  it('keeps two subTasks with the same agentId as distinct entries', () => {
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
            objective: 'Same agent twice',
            inputRefs: [],
            mode: 'PARALLEL_AGENTS',
            subTasks: [
              {
                subTaskId: 'st-1',
                agentId: 'shared-agent',
                agentName: 'Shared',
                objective: 'First pass',
              },
              {
                subTaskId: 'st-2',
                agentId: 'shared-agent',
                agentName: 'Shared',
                objective: 'Second pass',
              },
            ],
          },
        ],
      }),
    );

    const subTasks = state.attempts[1].steps.s1.subTasks;
    expect(Object.keys(subTasks).sort()).toEqual(['st-1', 'st-2']);
    expect(subTasks['st-1'].agentId).toBe('shared-agent');
    expect(subTasks['st-2'].agentId).toBe('shared-agent');
    expect(subTasks['st-1'].objective).toBe('First pass');
    expect(subTasks['st-2'].objective).toBe('Second pass');

    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e3',
        sequence: 3,
        eventType: 'SUBTASK_STARTED',
        stepId: 's1',
        subTaskId: 'st-1',
        agentId: 'shared-agent',
        agentName: 'Shared',
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e4',
        sequence: 4,
        eventType: 'SUBTASK_STARTED',
        stepId: 's1',
        subTaskId: 'st-2',
        agentId: 'shared-agent',
        agentName: 'Shared',
      }),
    );

    expect(state.attempts[1].steps.s1.subTasks['st-1'].status).toBe('RUNNING');
    expect(state.attempts[1].steps.s1.subTasks['st-2'].status).toBe('RUNNING');
  });
});
