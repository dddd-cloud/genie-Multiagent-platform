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
    schemaVersion: 1,
    requestId: 'req-1',
    runId: 'run-1',
    attemptNo: null,
    stepId: null,
    agentId: null,
    agentName: null,
    route: null,
    reasonCode: null,
    errorCode: null,
    steps: [],
    completionStatus: null,
    ...partial,
  };
}

function planCreated(sequence: number, attemptNo: number): OrchestrationEvent {
  return event({
    eventId: `req-1:${sequence}`,
    sequence,
    eventType: 'PLAN_CREATED',
    attemptNo,
    steps: [
      {
        stepId: 's1',
        agentId: 'a1',
        agentName: 'Agent One',
        objective: 'Do work',
        inputRefs: [],
      },
      {
        stepId: 's2',
        agentId: 'a2',
        agentName: 'Agent Two',
        objective: 'Follow up',
        inputRefs: ['s1'],
      },
    ],
  });
}

describe('EventV1CompatibilityTest', () => {
  it('still applies v1 REPLAN flow with schemaVersion 1', () => {
    let state = createInitialOrchestrationState();
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e1',
        sequence: 1,
        eventType: 'ROUTE_SELECTED',
        route: 'ORCHESTRATED',
        reasonCode: 'MULTI_STEP',
      }),
    );
    expect(state.schemaVersion).toBe(1);

    state = reduceOrchestrationEvent(state, planCreated(2, 1));
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e3',
        sequence: 3,
        eventType: 'STEP_STARTED',
        attemptNo: 1,
        stepId: 's1',
        agentId: 'a1',
        agentName: 'Agent One',
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e4',
        sequence: 4,
        eventType: 'STEP_FAILED',
        attemptNo: 1,
        stepId: 's1',
        agentId: 'a1',
        agentName: 'Agent One',
        errorCode: 'TOOL_TIMEOUT',
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e5',
        sequence: 5,
        eventType: 'STEP_SKIPPED',
        attemptNo: 1,
        stepId: 's2',
        agentId: 'a2',
        agentName: 'Agent Two',
        reasonCode: 'PRIOR_FAILED',
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e6',
        sequence: 6,
        eventType: 'REPLAN_STARTED',
        attemptNo: 2,
        reasonCode: 'RETRYABLE',
      }),
    );
    state = reduceOrchestrationEvent(state, planCreated(7, 2));

    expect(state.schemaVersion).toBe(1);
    expect(state.attempts[2].steps.s1.status).toBe('PLANNED');
    expect(state.attempts[2].steps.s2.status).toBe('PLANNED');

    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e8',
        sequence: 8,
        eventType: 'STEP_STARTED',
        attemptNo: 2,
        stepId: 's1',
        agentId: 'a1',
        agentName: 'Agent One',
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e9',
        sequence: 9,
        eventType: 'STEP_COMPLETED',
        attemptNo: 2,
        stepId: 's1',
        agentId: 'a1',
        agentName: 'Agent One',
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e10',
        sequence: 10,
        eventType: 'FINAL_RESPONSE',
        completionStatus: 'SUCCESS',
        route: 'ORCHESTRATED',
      }),
    );
    expect(state.terminalStatus).toBe('SUCCESS');
  });
});
