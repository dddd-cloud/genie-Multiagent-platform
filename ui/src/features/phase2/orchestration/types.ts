import type { OrchestrationRoute, StepMode } from '@/contracts';

export type StepUiStatus =
  | 'PLANNED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'
  | 'SKIPPED'
  | 'DEGRADED';

export type SubTaskUiStatus =
  | 'PLANNED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED';

export type TraceKind = 'STATUS' | 'THOUGHT' | 'OUTPUT' | 'ERROR';

export interface TraceLine {
  sequence: number;
  kind: TraceKind;
  text: string;
  truncated?: boolean;
}

export interface SubTaskUiState {
  subTaskId: string;
  agentId: string;
  agentName: string;
  objective: string;
  status: SubTaskUiStatus;
  retryNo: number;
  errorCode?: string | null;
  lines: TraceLine[];
  open: boolean;
}

export interface StepUiState {
  stepId: string;
  agentId: string;
  agentName: string;
  objective: string;
  status: StepUiStatus;
  stepMode?: StepMode | null;
  errorCode?: string | null;
  retryNo?: number | null;
  reviewing?: boolean;
  fallbackActive?: boolean;
  lines: TraceLine[];
  output?: string;
  open: boolean;
  /** Keyed by subTaskId — same agentId may appear twice. */
  subTasks: Record<string, SubTaskUiState>;
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
  schemaVersion: 1 | 2 | null;
}
