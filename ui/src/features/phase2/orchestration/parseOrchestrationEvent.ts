import {
  AGENT_TASK_ERROR_CODES,
  ORCHESTRATION_COMPLETION_STATUSES,
  ORCHESTRATION_EVENT_TYPES,
  ORCHESTRATION_ROUTES,
  STEP_MODES,
  type AgentTaskErrorCode,
  type OrchestrationCompletionStatus,
  type OrchestrationEvent,
  type OrchestrationEventType,
  type OrchestrationPlanStepView,
  type OrchestrationRoute,
  type OrchestrationSubTaskView,
  type StepMode,
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

function isStepMode(value: unknown): value is StepMode {
  return (
    typeof value === 'string' &&
    (STEP_MODES as readonly string[]).includes(value)
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

function parseSubTasks(raw: unknown): OrchestrationSubTaskView[] | null {
  if (raw === undefined || raw === null) return [];
  if (!Array.isArray(raw)) return null;
  const subTasks: OrchestrationSubTaskView[] = [];
  for (const item of raw) {
    if (!isRecord(item)) return null;
    if (
      !isNonEmptyString(item.subTaskId) ||
      !isNonEmptyString(item.agentId) ||
      typeof item.agentName !== 'string' ||
      !isNonEmptyString(item.objective)
    ) {
      return null;
    }
    subTasks.push({
      subTaskId: item.subTaskId,
      agentId: item.agentId,
      agentName: item.agentName,
      objective: item.objective,
    });
  }
  return subTasks;
}

function parsePlanSteps(
  raw: unknown,
  schemaVersion: 1 | 2,
): OrchestrationPlanStepView[] | null {
  if (!Array.isArray(raw)) return null;
  const steps: OrchestrationPlanStepView[] = [];
  for (const item of raw) {
    if (!isRecord(item)) return null;
    if (
      !isNonEmptyString(item.stepId) ||
      !isNonEmptyString(item.objective) ||
      !Array.isArray(item.inputRefs)
    ) {
      return null;
    }

    let mode: StepMode | null | undefined;
    if (item.mode === undefined) {
      mode = undefined;
    } else if (item.mode === null) {
      mode = null;
    } else if (isStepMode(item.mode)) {
      mode = item.mode;
    } else {
      return null;
    }

    const allowsNullAgent =
      schemaVersion === 2 &&
      (mode === 'MAIN_ONLY' || mode === 'PARALLEL_AGENTS');

    let agentId: string | null;
    if (isNonEmptyString(item.agentId)) {
      agentId = item.agentId;
    } else if (
      (item.agentId === null || item.agentId === undefined) &&
      allowsNullAgent
    ) {
      agentId = null;
    } else {
      return null;
    }

    let agentName: string | null;
    if (typeof item.agentName === 'string') {
      agentName = item.agentName;
    } else if (
      (item.agentName === null || item.agentName === undefined) &&
      allowsNullAgent
    ) {
      agentName = null;
    } else {
      return null;
    }

    const inputRefs: string[] = [];
    for (const ref of item.inputRefs) {
      if (typeof ref !== 'string') return null;
      inputRefs.push(ref);
    }

    const subTasks = parseSubTasks(item.subTasks);
    if (subTasks === null) return null;

    const step: OrchestrationPlanStepView = {
      stepId: item.stepId,
      agentId,
      agentName,
      objective: item.objective,
      inputRefs,
    };
    if (mode !== undefined) {
      step.mode = mode;
    }
    if (item.subTasks !== undefined) {
      step.subTasks = subTasks;
    }
    steps.push(step);
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

  if (raw.schemaVersion !== 1 && raw.schemaVersion !== 2) return null;
  const schemaVersion = raw.schemaVersion as 1 | 2;

  if (!isNonEmptyString(raw.eventId)) return null;
  if (
    typeof raw.sequence !== 'number' ||
    !Number.isInteger(raw.sequence) ||
    raw.sequence < 1
  ) {
    return null;
  }
  if (!isOrchestrationEventType(raw.eventType)) return null;
  if (!isNonEmptyString(raw.requestId)) return null;
  if (!isNonEmptyString(raw.runId)) return null;

  const steps = parsePlanSteps(raw.steps, schemaVersion);
  if (steps === null) return null;

  let attemptNo: number | null = null;
  if (raw.attemptNo !== null && raw.attemptNo !== undefined) {
    if (typeof raw.attemptNo !== 'number' || !Number.isInteger(raw.attemptNo)) {
      return null;
    }
    if (schemaVersion === 1) {
      if (raw.attemptNo < 1 || raw.attemptNo > 3) return null;
    } else if (raw.attemptNo !== 1) {
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

  let subTaskId: string | null = null;
  let stepMode: StepMode | null = null;
  let retryNo: number | null = null;

  if (raw.subTaskId !== null && raw.subTaskId !== undefined) {
    if (typeof raw.subTaskId !== 'string') return null;
    subTaskId = raw.subTaskId;
  }
  if (raw.stepMode !== null && raw.stepMode !== undefined) {
    if (!isStepMode(raw.stepMode)) return null;
    stepMode = raw.stepMode;
  }
  if (raw.retryNo !== null && raw.retryNo !== undefined) {
    if (
      typeof raw.retryNo !== 'number' ||
      !Number.isInteger(raw.retryNo) ||
      (raw.retryNo !== 0 && raw.retryNo !== 1)
    ) {
      return null;
    }
    retryNo = raw.retryNo;
  }

  return {
    schemaVersion,
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
    subTaskId,
    stepMode,
    retryNo,
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
