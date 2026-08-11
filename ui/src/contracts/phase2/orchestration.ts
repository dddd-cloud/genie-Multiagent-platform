export const ORCHESTRATION_ROUTES = [
  'DIRECT',
  'ORCHESTRATED',
] as const;

export type OrchestrationRoute = (typeof ORCHESTRATION_ROUTES)[number];

export const STEP_MODES = [
  'MAIN_ONLY',
  'SINGLE_AGENT',
  'PARALLEL_AGENTS',
] as const;

export type StepMode = (typeof STEP_MODES)[number];

export const ORCHESTRATION_EVENT_TYPES = [
  'ROUTE_SELECTED',
  'PLAN_CREATED',
  'STEP_STARTED',
  'STEP_COMPLETED',
  'STEP_FAILED',
  'STEP_SKIPPED',
  'REPLAN_STARTED',
  'SUMMARY_STARTED',
  'SUMMARY_COMPLETED',
  'SUMMARY_FALLBACK',
  'FINAL_RESPONSE',
  'PARALLEL_STARTED',
  'SUBTASK_STARTED',
  'SUBTASK_COMPLETED',
  'SUBTASK_FAILED',
  'STEP_REVIEW_STARTED',
  'STEP_RETRY_STARTED',
  'STEP_FALLBACK_STARTED',
  'STEP_DEGRADED',
] as const;

export type OrchestrationEventType = (typeof ORCHESTRATION_EVENT_TYPES)[number];

export const AGENT_TASK_ERROR_CODES = [
  'INVALID_INPUT',
  'AGENT_OFFLINE',
  'TOOL_PERMISSION_DENIED',
  'TOOL_TIMEOUT',
  'TOOL_UNAVAILABLE',
  'TOOL_INVALID_RESPONSE',
  'AGENT_INVALID_RESULT',
  'CONTEXT_BUDGET_EXCEEDED',
  'EXECUTION_ERROR',
  'CANCELLED',
] as const;

export type AgentTaskErrorCode = (typeof AGENT_TASK_ERROR_CODES)[number];

export const ORCHESTRATION_COMPLETION_STATUSES = [
  'SUCCESS',
  'PARTIAL',
] as const;

export type OrchestrationCompletionStatus =
  (typeof ORCHESTRATION_COMPLETION_STATUSES)[number];

export interface OrchestrationSubTaskView {
  subTaskId: string;
  agentId: string;
  agentName: string;
  objective: string;
}

export interface OrchestrationPlanStepView {
  stepId: string;
  agentId: string | null;
  agentName: string | null;
  objective: string;
  inputRefs: string[];
  mode?: StepMode | null;
  subTasks?: OrchestrationSubTaskView[];
}

export interface OrchestrationEvent {
  schemaVersion: 1 | 2;
  eventId: string;
  sequence: number;
  eventType: OrchestrationEventType;
  requestId: string;
  runId: string;
  attemptNo: number | null;
  stepId: string | null;
  agentId: string | null;
  agentName: string | null;
  route: OrchestrationRoute | null;
  reasonCode: string | null;
  errorCode: AgentTaskErrorCode | null;
  steps: OrchestrationPlanStepView[];
  completionStatus: OrchestrationCompletionStatus | null;
  subTaskId?: string | null;
  stepMode?: StepMode | null;
  retryNo?: number | null;
}
