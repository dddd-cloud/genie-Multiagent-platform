import type { TraceKind } from './types';

export interface OrchestrationTrace {
  schemaVersion: number;
  sequence: number;
  requestId: string;
  runId: string;
  scope: 'MAIN' | 'STEP' | 'SUBTASK';
  attemptNo: number | null;
  stepId: string | null;
  agentId: string | null;
  agentName: string | null;
  kind: TraceKind;
  text: string;
  append: boolean;
  truncated: boolean;
  subTaskId?: string | null;
  retryNo?: number | null;
}

const TRACE_KINDS = new Set<string>(['STATUS', 'THOUGHT', 'OUTPUT', 'ERROR']);
const TRACE_SCOPES_V1 = new Set<string>(['MAIN', 'STEP']);
const TRACE_SCOPES_V2 = new Set<string>(['MAIN', 'STEP', 'SUBTASK']);

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0;
}

function isTraceKind(value: unknown): value is TraceKind {
  return typeof value === 'string' && TRACE_KINDS.has(value);
}

/**
 * Parse a live orchestrationTrace payload (parallel to frozen orchestrationEvent).
 */
export function parseOrchestrationTrace(raw: unknown): OrchestrationTrace | null {
  if (!isRecord(raw)) return null;
  if (raw.schemaVersion !== 1 && raw.schemaVersion !== 2) return null;
  const schemaVersion = raw.schemaVersion as 1 | 2;

  if (typeof raw.sequence !== 'number' || raw.sequence < 1) return null;
  if (!isNonEmptyString(raw.requestId) || !isNonEmptyString(raw.runId)) {
    return null;
  }

  const allowedScopes = schemaVersion === 1 ? TRACE_SCOPES_V1 : TRACE_SCOPES_V2;
  if (typeof raw.scope !== 'string' || !allowedScopes.has(raw.scope)) {
    return null;
  }
  if (!isTraceKind(raw.kind)) return null;
  if (typeof raw.text !== 'string') return null;

  const attemptNo =
    raw.attemptNo === null || raw.attemptNo === undefined
      ? null
      : typeof raw.attemptNo === 'number'
        ? raw.attemptNo
        : null;
  if (
    raw.attemptNo !== null &&
    raw.attemptNo !== undefined &&
    typeof raw.attemptNo !== 'number'
  ) {
    return null;
  }
  if (schemaVersion === 2 && attemptNo !== null && attemptNo !== 1) {
    return null;
  }

  const stepId =
    raw.stepId === null || raw.stepId === undefined
      ? null
      : typeof raw.stepId === 'string'
        ? raw.stepId
        : null;
  if (
    raw.stepId !== null &&
    raw.stepId !== undefined &&
    typeof raw.stepId !== 'string'
  ) {
    return null;
  }

  const agentId =
    raw.agentId === null || raw.agentId === undefined
      ? null
      : typeof raw.agentId === 'string'
        ? raw.agentId
        : null;
  if (
    raw.agentId !== null &&
    raw.agentId !== undefined &&
    typeof raw.agentId !== 'string'
  ) {
    return null;
  }

  const agentName =
    raw.agentName === null || raw.agentName === undefined
      ? null
      : typeof raw.agentName === 'string'
        ? raw.agentName
        : null;
  if (
    raw.agentName !== null &&
    raw.agentName !== undefined &&
    typeof raw.agentName !== 'string'
  ) {
    return null;
  }

  let subTaskId: string | null = null;
  let retryNo: number | null = null;
  if (schemaVersion === 2) {
    if (raw.subTaskId !== null && raw.subTaskId !== undefined) {
      if (typeof raw.subTaskId !== 'string') return null;
      subTaskId = raw.subTaskId;
    }
    if (raw.scope === 'SUBTASK' && !isNonEmptyString(subTaskId)) {
      return null;
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
  }

  return {
    schemaVersion,
    sequence: raw.sequence,
    requestId: raw.requestId,
    runId: raw.runId,
    scope: raw.scope as 'MAIN' | 'STEP' | 'SUBTASK',
    attemptNo,
    stepId,
    agentId,
    agentName,
    kind: raw.kind,
    text: raw.text,
    append: raw.append === true,
    truncated: raw.truncated === true,
    subTaskId,
    retryNo,
  };
}

export function extractOrchestrationTraceFromResult(
  result: unknown,
): OrchestrationTrace | null {
  if (!isRecord(result)) return null;
  if (result.packageType === 'orchestration_trace') {
    const resultMap = result.resultMap;
    if (!isRecord(resultMap)) return null;
    return parseOrchestrationTrace(resultMap.orchestrationTrace);
  }
  const resultMap = result.resultMap;
  if (!isRecord(resultMap)) return null;
  return parseOrchestrationTrace(resultMap.orchestrationTrace);
}
