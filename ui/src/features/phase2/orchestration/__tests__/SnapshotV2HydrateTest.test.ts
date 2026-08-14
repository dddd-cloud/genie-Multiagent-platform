import { describe, expect, it } from 'vitest';
import type { OrchestrationEvent } from '@/contracts';
import { replayOrchestrationEvents } from '../replayOrchestrationEvents';

function event(
  partial: Partial<OrchestrationEvent> &
    Pick<OrchestrationEvent, 'eventId' | 'sequence' | 'eventType'>,
): OrchestrationEvent {
  return {
    schemaVersion: 2,
    requestId: 'req-v2',
    runId: 'run-v2',
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

describe('SnapshotV2HydrateTest', () => {
  it('replays v2 serial events to SUCCESS', () => {
    const state = replayOrchestrationEvents([
      event({
        eventId: 'v2:1',
        sequence: 1,
        eventType: 'ROUTE_SELECTED',
        attemptNo: null,
        route: 'ORCHESTRATED',
        reasonCode: 'MULTI_STEP',
      }),
      event({
        eventId: 'v2:2',
        sequence: 2,
        eventType: 'PLAN_CREATED',
        steps: [
          {
            stepId: 's1',
            agentId: 'a1',
            agentName: 'Agent One',
            objective: 'Serial work',
            inputRefs: [],
            mode: 'SINGLE_AGENT',
          },
        ],
      }),
      event({
        eventId: 'v2:3',
        sequence: 3,
        eventType: 'STEP_STARTED',
        stepId: 's1',
        agentId: 'a1',
        agentName: 'Agent One',
      }),
      event({
        eventId: 'v2:4',
        sequence: 4,
        eventType: 'STEP_COMPLETED',
        stepId: 's1',
        agentId: 'a1',
        agentName: 'Agent One',
      }),
      event({
        eventId: 'v2:5',
        sequence: 5,
        eventType: 'SUMMARY_STARTED',
      }),
      event({
        eventId: 'v2:6',
        sequence: 6,
        eventType: 'SUMMARY_COMPLETED',
      }),
      event({
        eventId: 'v2:7',
        sequence: 7,
        eventType: 'FINAL_RESPONSE',
        attemptNo: null,
        completionStatus: 'SUCCESS',
        route: 'ORCHESTRATED',
      }),
    ]);

    expect(state.schemaVersion).toBe(2);
    expect(state.terminalStatus).toBe('SUCCESS');
    expect(state.summaryStatus).toBe('COMPLETED');
    expect(state.attempts[1].steps.s1.status).toBe('COMPLETED');
    expect(state.attempts[1].steps.s1.subTasks).toEqual({});
  });

  it('replays v2 parallel events to PARTIAL', () => {
    const state = replayOrchestrationEvents([
      event({
        eventId: 'p:1',
        sequence: 1,
        eventType: 'ROUTE_SELECTED',
        attemptNo: null,
        route: 'ORCHESTRATED',
        reasonCode: 'MULTI_AGENT',
      }),
      event({
        eventId: 'p:2',
        sequence: 2,
        eventType: 'PLAN_CREATED',
        steps: [
          {
            stepId: 's1',
            agentId: null,
            agentName: null,
            objective: 'Parallel work',
            inputRefs: [],
            mode: 'PARALLEL_AGENTS',
            subTasks: [
              {
                subTaskId: 'st-a',
                agentId: 'agent-a',
                agentName: 'A',
                objective: 'A',
              },
              {
                subTaskId: 'st-b',
                agentId: 'agent-b',
                agentName: 'B',
                objective: 'B',
              },
            ],
          },
        ],
      }),
      event({
        eventId: 'p:3',
        sequence: 3,
        eventType: 'PARALLEL_STARTED',
        stepId: 's1',
        stepMode: 'PARALLEL_AGENTS',
      }),
      event({
        eventId: 'p:4',
        sequence: 4,
        eventType: 'SUBTASK_STARTED',
        stepId: 's1',
        subTaskId: 'st-a',
        agentId: 'agent-a',
        agentName: 'A',
      }),
      event({
        eventId: 'p:5',
        sequence: 5,
        eventType: 'SUBTASK_STARTED',
        stepId: 's1',
        subTaskId: 'st-b',
        agentId: 'agent-b',
        agentName: 'B',
      }),
      event({
        eventId: 'p:6',
        sequence: 6,
        eventType: 'SUBTASK_COMPLETED',
        stepId: 's1',
        subTaskId: 'st-a',
        agentId: 'agent-a',
        agentName: 'A',
      }),
      event({
        eventId: 'p:7',
        sequence: 7,
        eventType: 'SUBTASK_FAILED',
        stepId: 's1',
        subTaskId: 'st-b',
        agentId: 'agent-b',
        agentName: 'B',
        errorCode: 'EXECUTION_ERROR',
      }),
      event({
        eventId: 'p:8',
        sequence: 8,
        eventType: 'STEP_COMPLETED',
        stepId: 's1',
      }),
      event({
        eventId: 'p:9',
        sequence: 9,
        eventType: 'FINAL_RESPONSE',
        attemptNo: null,
        completionStatus: 'PARTIAL',
        route: 'ORCHESTRATED',
      }),
    ]);

    expect(state.schemaVersion).toBe(2);
    expect(state.terminalStatus).toBe('PARTIAL');
    expect(state.attempts[1].steps.s1.status).toBe('COMPLETED');
    expect(state.attempts[1].steps.s1.subTasks['st-a'].status).toBe(
      'COMPLETED',
    );
    expect(state.attempts[1].steps.s1.subTasks['st-b'].status).toBe('FAILED');
  });
});
