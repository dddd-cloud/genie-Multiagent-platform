import type { OrchestrationEvent } from '@/contracts';
import {
  createInitialOrchestrationState,
  reduceOrchestrationEvent,
} from './orchestrationReducer';
import type { OrchestrationUiState } from './types';

/**
 * Replay an ordered (or partially ordered) list of orchestration events
 * through the reducer, starting from an optional initial state.
 */
export function replayOrchestrationEvents(
  events: readonly OrchestrationEvent[],
  initialState: OrchestrationUiState = createInitialOrchestrationState(),
): OrchestrationUiState {
  let state = initialState;
  for (const event of events) {
    state = reduceOrchestrationEvent(state, event);
  }
  return state;
}
