export const ORCHESTRATION_ROUTES = [
  'DIRECT',
  'ORCHESTRATED',
] as const;

export type OrchestrationRoute = (typeof ORCHESTRATION_ROUTES)[number];

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

export interface OrchestrationPlanStepView {
  stepId: string;
  agentId: string;
  agentName: string;
  objective: string;
  inputRefs: string[];
}

export interface OrchestrationEvent {
  schemaVersion: 1;
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
}
