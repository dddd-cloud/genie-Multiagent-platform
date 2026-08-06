import type { OrchestrationRoute } from '@/contracts';

export type StepUiStatus =
  | 'PLANNED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'
  | 'SKIPPED';

export type TraceKind = 'STATUS' | 'THOUGHT' | 'OUTPUT' | 'ERROR';

export interface TraceLine {
  sequence: number;
  kind: TraceKind;
  text: string;
  truncated?: boolean;
}

export interface StepUiState {
  stepId: string;
  agentId: string;
  agentName: string;
  objective: string;
  status: StepUiStatus;
  errorCode?: string | null;
  lines: TraceLine[];
  output?: string;
  open: boolean;
}

export interface AttemptUiState {
  attemptNo: number;
  steps: Record<string, StepUiState>;
}

export interface MainAgentUiState {
  open: boolean;
  lines: TraceLine[];
}

export interface OrchestrationUiState {
  route: OrchestrationRoute | null;
  routeReasonCode: string | null;
  attempts: Record<number, AttemptUiState>;
  summaryStatus: 'IDLE' | 'RUNNING' | 'COMPLETED' | 'FALLBACK';
  terminalStatus: 'RUNNING' | 'SUCCESS' | 'PARTIAL' | 'FAILED' | 'INTERRUPTED';
  lastSequence: number;
  lastTraceSequence: number;
  seenEventIds: Record<string, true>;
  recoveryWarnings: string[];
  masterOpen: boolean;
  main: MainAgentUiState;
  phaseLabel: 'thinking' | 'done';
}
