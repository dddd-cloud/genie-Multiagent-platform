import type {
  ConversationMessageResponse,
  GptProcessResultEvent,
  OutputStyle,
} from '@/contracts';
import { OUTPUT_STYLES } from '@/contracts';
import { combineData, extractGeneratedFiles, handleTaskData } from '@/utils/chat';
import { extractOrchestrationEventFromResult } from '@/features/phase2/orchestration/parseOrchestrationEvent';
import { extractOrchestrationTraceFromResult } from '@/features/phase2/orchestration/parseOrchestrationTrace';
import {
  createInitialOrchestrationState,
  markOrchestrationDone,
  markOrchestrationInterrupted,
  reduceOrchestrationEvent,
  reduceOrchestrationTrace,
} from '@/features/phase2/orchestration/orchestrationReducer';
import type { OrchestrationUiState } from '@/features/phase2/orchestration/types';
import { extractBrowserSkillSignalFromResult } from '@/features/phase2/skillRuntime/signal';
import { USER_STOPPED_COPY } from './liveChatRuns';
import { parseSnapshot } from './snapshot';
import type { PersistedChatItem } from './types';

function resolveOutputStyle(value: string | null | undefined): OutputStyle {
  if (value && (OUTPUT_STYLES as readonly string[]).includes(value)) {
    return value as OutputStyle;
  }
  return 'docs';
}

function roleOrder(role: ConversationMessageResponse['role']): number {
  return role === 'USER' ? 0 : 1;
}

function sortMessages(
  messages: ConversationMessageResponse[],
): ConversationMessageResponse[] {
  return messages.slice().sort((a, b) => {
    if (a.turnNo !== b.turnNo) {
      return a.turnNo - b.turnNo;
    }
    return roleOrder(a.role) - roleOrder(b.role);
  });
}

function createBaseChatItem(
  conversationId: string,
  requestId: string,
  query: string,
  deepThink: boolean,
  outputStyle: OutputStyle,
): PersistedChatItem {
  // Plan §12.3: USER base fields only — no live-stream tip on history restore.
  return {
    query,
    files: [],
    responseType: 'txt',
    sessionId: conversationId,
    requestId,
    loading: false,
    forceStop: false,
    tasks: [],
    thought: '',
    response: '',
    taskStatus: 0,
    tip: '',
    multiAgent: { tasks: [] },
    deepThink,
    outputStyle,
  };
}

function extractEventData(
  event: GptProcessResultEvent,
): MESSAGE.EventData | undefined {
  const resultMap = event.resultMap;
  if (!resultMap || typeof resultMap !== 'object') {
    return undefined;
  }
  const eventData = resultMap.eventData;
  if (!eventData || typeof eventData !== 'object' || Array.isArray(eventData)) {
    return undefined;
  }
  return eventData as unknown as MESSAGE.EventData;
}

function extractResponseFromEvents(
  events: GptProcessResultEvent[],
  fallback: string | null,
): string {
  for (let i = events.length - 1; i >= 0; i -= 1) {
    const event = events[i];
    if (!event.finished) {
      continue;
    }
    if (event.responseAll) {
      return event.responseAll;
    }
    if (event.response) {
      return event.response;
    }
  }
  return fallback ?? '';
}

function hasNonEmptyContent(content: string | null | undefined): boolean {
  return typeof content === 'string' && content.length > 0;
}

function replaySnapshot(
  item: PersistedChatItem,
  events: GptProcessResultEvent[],
): void {
  let orchestration: OrchestrationUiState | undefined = item.orchestration;
  let sawOrchestration = false;

  for (const event of events) {
    if (event.packageType === 'heartbeat') {
      continue;
    }

    // Hard rule: snapshot skill_execution packets must never re-execute Python.
    if (event.packageType === 'skill_execution') {
      // Touch parser so malformed signals stay inert; no runner call.
      extractBrowserSkillSignalFromResult(event);
      continue;
    }

    const orchEvent = extractOrchestrationEventFromResult(event);
    if (orchEvent) {
      if (!orchestration) {
        orchestration = createInitialOrchestrationState();
      }
      orchestration = reduceOrchestrationEvent(orchestration, orchEvent);
      sawOrchestration = true;
    }

    const orchTrace = extractOrchestrationTraceFromResult(event);
    if (orchTrace) {
      if (!orchestration) {
        orchestration = createInitialOrchestrationState();
      }
      orchestration = reduceOrchestrationTrace(orchestration, orchTrace);
      sawOrchestration = true;
    }

    // Orchestration progress / live-trace packages restore work panel only —
    // never combineData or append chat body from them.
    if (
      event.packageType === 'orchestration' ||
      event.packageType === 'orchestration_trace'
    ) {
      continue;
    }

    const eventData = extractEventData(event);
    if (eventData) {
      combineData(eventData, item);
    }
    if (event.responseAll) {
      item.response = event.responseAll;
    } else if (event.response) {
      item.response = event.response;
    }
    const generated = extractGeneratedFiles(event.resultMap);
    if (generated.length) {
      item.generatedFiles = generated;
    }
  }

  if (sawOrchestration && orchestration) {
    // Snapshot hydrate restores plan/outputs; UI stays collapsed by default.
    item.orchestration = {
      ...orchestration,
      masterOpen: false,
      main: { ...orchestration.main, open: false },
      attempts: Object.fromEntries(
        Object.entries(orchestration.attempts).map(([key, attempt]) => [
          Number(key),
          {
            ...attempt,
            steps: Object.fromEntries(
              Object.entries(attempt.steps).map(([stepId, step]) => [
                stepId,
                {
                  ...step,
                  open: false,
                  subTasks: Object.fromEntries(
                    Object.entries(step.subTasks ?? {}).map(([subId, sub]) => [
                      subId,
                      { ...sub, open: false },
                    ]),
                  ),
                },
              ]),
            ),
          },
        ]),
      ),
      phaseLabel:
        orchestration.terminalStatus === 'RUNNING' ? 'thinking' : 'done',
    };
    if (item.orchestration.terminalStatus !== 'RUNNING') {
      item.orchestration = markOrchestrationDone(item.orchestration);
    }
  }

  handleTaskData(item, item.deepThink, item.multiAgent);
}

function applyAssistant(
  item: PersistedChatItem,
  assistant: ConversationMessageResponse,
): void {
  item.persistedStatus = assistant.status;
  item.errorCode = assistant.errorCode;
  item.errorMessage = assistant.errorMessage;

  // Plan §12.4: message payloadVersion must be 1; otherwise content fallback.
  const messageVersionOk = assistant.payloadVersion === 1;
  const envelope = messageVersionOk
    ? parseSnapshot(assistant.streamSnapshot)
    : null;
  const canReplay = !!envelope;

  if (canReplay && envelope) {
    try {
      replaySnapshot(item, envelope.events);
      if (envelope.truncated) {
        item.snapshotTruncated = true;
      }

      if (assistant.status === 'COMPLETED') {
        // Snapshot restores process/timeline only; non-empty content is final text.
        if (hasNonEmptyContent(assistant.content)) {
          item.response = assistant.content as string;
        } else if (!item.response) {
          item.response = extractResponseFromEvents(envelope.events, null);
        }
      } else if (
        assistant.status === 'FAILED' ||
        assistant.status === 'INTERRUPTED'
      ) {
        // Snapshot restores process; content is fallback for body.
        if (!item.response && assistant.content) {
          item.response = assistant.content;
        }
      } else if (!item.response) {
        item.response = extractResponseFromEvents(
          envelope.events,
          assistant.content,
        );
      }
    } catch {
      item.response = assistant.content ?? '';
      item.multiAgent = { tasks: [] };
      item.tasks = [];
    }
  } else {
    item.response = assistant.content ?? '';
  }

  // Content fallback: ensure response is set when snapshot yields nothing.
  if (!item.response && assistant.content) {
    item.response = assistant.content;
  }

  if (
    item.orchestration &&
    item.orchestration.recoveryWarnings.length > 0
  ) {
    item.orchestrationRecoveryWarning = true;
  }

  switch (assistant.status) {
    case 'COMPLETED':
      item.loading = false;
      break;
    case 'FAILED':
      item.loading = false;
      item.errorCode = assistant.errorCode;
      item.errorMessage = assistant.errorMessage;
      break;
    case 'INTERRUPTED':
      item.loading = false;
      if (assistant.errorCode === 'CLIENT_DISCONNECTED') {
        item.stoppedByUser = true;
        item.response = USER_STOPPED_COPY;
        item.errorCode = null;
        item.errorMessage = null;
        if (item.orchestration) {
          item.orchestration = markOrchestrationInterrupted(item.orchestration);
        }
        break;
      }
      item.errorCode = assistant.errorCode;
      item.errorMessage =
        assistant.errorMessage ??
        (assistant.errorCode === 'SERVICE_RESTARTED'
          ? '服务重启导致本次执行中断，可重新发送'
          : '本次执行已中断，可重新发送');
      break;
    case 'PENDING':
    case 'STREAMING':
      item.loading = true;
      item.persistedStatus = assistant.status;
      break;
    default:
      item.loading = false;
      break;
  }
}

/**
 * Map persisted conversation messages into ChatView-compatible chat items.
 * sessionId on each item must equal conversationId (route / GptQueryReq.sessionId).
 */
export function hydrateConversation(
  messages: ConversationMessageResponse[],
  conversationId: string,
): PersistedChatItem[] {
  const sorted = sortMessages(messages);
  const groups = new Map<
    string,
    { user?: ConversationMessageResponse; assistant?: ConversationMessageResponse }
  >();
  const order: string[] = [];

  for (const message of sorted) {
    const key = message.requestId || `turn-${message.turnNo}`;
    if (!groups.has(key)) {
      groups.set(key, {});
      order.push(key);
    }
    const group = groups.get(key)!;
    if (message.role === 'USER') {
      group.user = message;
    } else {
      group.assistant = message;
    }
  }

  const result: PersistedChatItem[] = [];

  for (const key of order) {
    const group = groups.get(key)!;
    const user = group.user;
    const assistant = group.assistant;
    const requestId = user?.requestId || assistant?.requestId || key;
    // Per-turn mode comes from the USER message for that turn.
    const deepThink = user?.deepThink === 1;
    const outputStyle = resolveOutputStyle(user?.outputStyle);
    const query = user?.content ?? '';

    const item = createBaseChatItem(
      conversationId,
      requestId,
      query,
      deepThink,
      outputStyle,
    );

    if (assistant) {
      applyAssistant(item, assistant);
    } else {
      item.loading = false;
    }

    result.push(item);
  }

  return result;
}
