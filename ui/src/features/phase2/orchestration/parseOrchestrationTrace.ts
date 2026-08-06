import type { TraceKind } from './types';

export interface OrchestrationTrace {
  schemaVersion: number;
  sequence: number;
  requestId: string;
  runId: string;
  scope: 'MAIN' | 'STEP';
  attemptNo: number | null;
  stepId: string | null;
  agentId: string | null;
  agentName: string | null;
  kind: TraceKind;
  text: string;
  append: boolean;
  truncated: boolean;
}

const TRACE_KINDS = new Set<string>(['STATUS', 'THOUGHT', 'OUTPUT', 'ERROR']);
const TRACE_SCOPES = new Set<string>(['MAIN', 'STEP']);

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
  if (raw.schemaVersion !== 1) return null;
  if (typeof raw.sequence !== 'number' || raw.sequence < 1) return null;
  if (!isNonEmptyString(raw.requestId) || !isNonEmptyString(raw.runId)) {
    return null;
  }
  if (typeof raw.scope !== 'string' || !TRACE_SCOPES.has(raw.scope)) {
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
  const stepId =
    raw.stepId === null || raw.stepId === undefined
      ? null
      : typeof raw.stepId === 'string'
        ? raw.stepId
        : null;
  const agentId =
    raw.agentId === null || raw.agentId === undefined
      ? null
      : typeof raw.agentId === 'string'
        ? raw.agentId
        : null;
  const agentName =
    raw.agentName === null || raw.agentName === undefined
      ? null
      : typeof raw.agentName === 'string'
        ? raw.agentName
        : null;

  return {
    schemaVersion: 1,
    sequence: raw.sequence,
    requestId: raw.requestId,
    runId: raw.runId,
    scope: raw.scope as 'MAIN' | 'STEP',
    attemptNo,
    stepId,
    agentId,
    agentName,
    kind: raw.kind,
    text: raw.text,
    append: raw.append === true,
    truncated: raw.truncated === true,
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
