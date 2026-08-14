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

describe('RetryFallbackDegradedUiTest', () => {
  it('applies STEP_RETRY_STARTED, STEP_FALLBACK_STARTED, and STEP_DEGRADED', () => {
    let state = createInitialOrchestrationState();
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e1',
        sequence: 1,
        eventType: 'ROUTE_SELECTED',
        attemptNo: null,
        route: 'ORCHESTRATED',
        reasonCode: 'MULTI_STEP',
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
            agentId: 'a1',
            agentName: 'Agent One',
            objective: 'Do work',
            inputRefs: [],
            mode: 'SINGLE_AGENT',
          },
        ],
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e3',
        sequence: 3,
        eventType: 'STEP_STARTED',
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
        eventType: 'STEP_REVIEW_STARTED',
        stepId: 's1',
        agentId: 'a1',
        agentName: 'Agent One',
      }),
    );
    expect(state.attempts[1].steps.s1.reviewing).toBe(true);

    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e5',
        sequence: 5,
        eventType: 'STEP_RETRY_STARTED',
        stepId: 's1',
        agentId: 'a1',
        agentName: 'Agent One',
        retryNo: 1,
      }),
    );
    expect(state.attempts[1].steps.s1.status).toBe('RUNNING');
    expect(state.attempts[1].steps.s1.retryNo).toBe(1);
    expect(state.attempts[1].steps.s1.reviewing).toBe(false);
    expect(state.attempts[1].steps.s1.fallbackActive).toBe(false);

    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e6',
        sequence: 6,
        eventType: 'STEP_FALLBACK_STARTED',
        stepId: 's1',
        agentId: 'a1',
        agentName: 'Agent One',
      }),
    );
    expect(state.attempts[1].steps.s1.fallbackActive).toBe(true);
    expect(state.attempts[1].steps.s1.status).toBe('RUNNING');

    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e7',
        sequence: 7,
        eventType: 'STEP_DEGRADED',
        stepId: 's1',
        agentId: 'a1',
        agentName: 'Agent One',
        reasonCode: 'FALLBACK_USED',
      }),
    );
    expect(state.attempts[1].steps.s1.status).toBe('DEGRADED');
    expect(state.attempts[1].steps.s1.fallbackActive).toBe(false);
  });
});
