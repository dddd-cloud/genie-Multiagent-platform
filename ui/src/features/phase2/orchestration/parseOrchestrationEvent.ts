import {
  AGENT_TASK_ERROR_CODES,
  ORCHESTRATION_COMPLETION_STATUSES,
  ORCHESTRATION_EVENT_TYPES,
  ORCHESTRATION_ROUTES,
  type AgentTaskErrorCode,
  type OrchestrationCompletionStatus,
  type OrchestrationEvent,
  type OrchestrationEventType,
  type OrchestrationPlanStepView,
  type OrchestrationRoute,
} from '@/contracts';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0;
}

function isOrchestrationEventType(
  value: unknown,
): value is OrchestrationEventType {
  return (
    typeof value === 'string' &&
    (ORCHESTRATION_EVENT_TYPES as readonly string[]).includes(value)
  );
}

function isOrchestrationRoute(value: unknown): value is OrchestrationRoute {
  return (
    typeof value === 'string' &&
    (ORCHESTRATION_ROUTES as readonly string[]).includes(value)
  );
}

function isAgentTaskErrorCode(value: unknown): value is AgentTaskErrorCode {
  return (
    typeof value === 'string' &&
    (AGENT_TASK_ERROR_CODES as readonly string[]).includes(value)
  );
}

function isCompletionStatus(
  value: unknown,
): value is OrchestrationCompletionStatus {
  return (
    typeof value === 'string' &&
    (ORCHESTRATION_COMPLETION_STATUSES as readonly string[]).includes(value)
  );
}

function parsePlanSteps(raw: unknown): OrchestrationPlanStepView[] | null {
  if (!Array.isArray(raw)) return null;
  const steps: OrchestrationPlanStepView[] = [];
  for (const item of raw) {
    if (!isRecord(item)) return null;
    if (
      !isNonEmptyString(item.stepId) ||
      !isNonEmptyString(item.agentId) ||
      typeof item.agentName !== 'string' ||
      !isNonEmptyString(item.objective) ||
      !Array.isArray(item.inputRefs)
    ) {
      return null;
    }
    const inputRefs: string[] = [];
    for (const ref of item.inputRefs) {
      if (typeof ref !== 'string') return null;
      inputRefs.push(ref);
    }
    steps.push({
      stepId: item.stepId,
      agentId: item.agentId,
      agentName: item.agentName,
      objective: item.objective,
      inputRefs,
    });
  }
  return steps;
}

/**
 * Validate and normalize an OrchestrationEvent from an unknown payload.
 * Returns null when the payload is not a valid event.
 */
export function parseOrchestrationEvent(
  raw: unknown,
): OrchestrationEvent | null {
  if (!isRecord(raw)) return null;

  if (raw.schemaVersion !== 1) return null;
  if (!isNonEmptyString(raw.eventId)) return null;
  if (typeof raw.sequence !== 'number' || !Number.isInteger(raw.sequence) || raw.sequence < 1) {
    return null;
  }
  if (!isOrchestrationEventType(raw.eventType)) return null;
  if (!isNonEmptyString(raw.requestId)) return null;
  if (!isNonEmptyString(raw.runId)) return null;

  const steps = parsePlanSteps(raw.steps);
  if (steps === null) return null;

  let attemptNo: number | null = null;
  if (raw.attemptNo !== null && raw.attemptNo !== undefined) {
    if (
      typeof raw.attemptNo !== 'number' ||
      !Number.isInteger(raw.attemptNo) ||
      raw.attemptNo < 1 ||
      raw.attemptNo > 3
    ) {
      return null;
    }
    attemptNo = raw.attemptNo;
  }

  let stepId: string | null = null;
  if (raw.stepId !== null && raw.stepId !== undefined) {
    if (typeof raw.stepId !== 'string') return null;
    stepId = raw.stepId;
  }

  let agentId: string | null = null;
  if (raw.agentId !== null && raw.agentId !== undefined) {
    if (typeof raw.agentId !== 'string') return null;
    agentId = raw.agentId;
  }

  let agentName: string | null = null;
  if (raw.agentName !== null && raw.agentName !== undefined) {
    if (typeof raw.agentName !== 'string') return null;
    agentName = raw.agentName;
  }

  let route: OrchestrationRoute | null = null;
  if (raw.route !== null && raw.route !== undefined) {
    if (!isOrchestrationRoute(raw.route)) return null;
    route = raw.route;
  }

  let reasonCode: string | null = null;
  if (raw.reasonCode !== null && raw.reasonCode !== undefined) {
    if (typeof raw.reasonCode !== 'string') return null;
    reasonCode = raw.reasonCode;
  }

  let errorCode: AgentTaskErrorCode | null = null;
  if (raw.errorCode !== null && raw.errorCode !== undefined) {
    if (!isAgentTaskErrorCode(raw.errorCode)) return null;
    errorCode = raw.errorCode;
  }

  let completionStatus: OrchestrationCompletionStatus | null = null;
  if (raw.completionStatus !== null && raw.completionStatus !== undefined) {
    if (!isCompletionStatus(raw.completionStatus)) return null;
    completionStatus = raw.completionStatus;
  }

  return {
    schemaVersion: 1,
    eventId: raw.eventId,
    sequence: raw.sequence,
    eventType: raw.eventType,
    requestId: raw.requestId,
    runId: raw.runId,
    attemptNo,
    stepId,
    agentId,
    agentName,
    route,
    reasonCode,
    errorCode,
    steps,
    completionStatus,
  };
}

/**
 * Extract OrchestrationEvent from an SSE GptProcessResult-like payload.
 */
export function extractOrchestrationEventFromResult(
  result: unknown,
): OrchestrationEvent | null {
  if (!isRecord(result)) return null;
  const resultMap = result.resultMap;
  if (!isRecord(resultMap)) return null;
  return parseOrchestrationEvent(resultMap.orchestrationEvent);
}
