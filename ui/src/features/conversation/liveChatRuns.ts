import { markOrchestrationInterrupted } from '@/features/phase2/orchestration/orchestrationReducer';
import type { SseHandle } from '@/utils/querySSE';
import type { PersistedChatItem } from './types';

export const USER_STOPPED_COPY = '已停止生成。你可以继续提问。';

export type LiveChatRunSnapshot = {
  conversationId: string;
  requestId: string;
  chatList: PersistedChatItem[];
  sendInFlight: boolean;
  userStopped: boolean;
};

type Listener = (snapshot: LiveChatRunSnapshot) => void;

type LiveChatRun = LiveChatRunSnapshot & {
  handle: SseHandle | null;
  skillAbort: AbortController | null;
};

const runs = new Map<string, LiveChatRun>();
const listeners = new Map<string, Set<Listener>>();

function toSnapshot(run: LiveChatRun): LiveChatRunSnapshot {
  return {
    conversationId: run.conversationId,
    requestId: run.requestId,
    chatList: run.chatList,
    sendInFlight: run.sendInFlight,
    userStopped: run.userStopped,
  };
}

function notify(conversationId: string): void {
  const run = runs.get(conversationId);
  if (!run) {
    return;
  }
  const snapshot = toSnapshot(run);
  for (const listener of [...(listeners.get(conversationId) ?? [])]) {
    listener(snapshot);
  }
}

function prune(conversationId: string): void {
  const run = runs.get(conversationId);
  const subs = listeners.get(conversationId);
  if (run?.sendInFlight) {
    return;
  }
  if (subs && subs.size > 0) {
    return;
  }
  runs.delete(conversationId);
  if (!subs || subs.size === 0) {
    listeners.delete(conversationId);
  }
}

export function peekLiveChatRun(
  conversationId: string,
): LiveChatRunSnapshot | null {
  const run = runs.get(conversationId);
  return run ? toSnapshot(run) : null;
}

export function getLiveChatRunHandle(
  conversationId: string,
): SseHandle | null {
  return runs.get(conversationId)?.handle ?? null;
}

export function subscribeLiveChatRun(
  conversationId: string,
  listener: Listener,
): () => void {
  let subs = listeners.get(conversationId);
  if (!subs) {
    subs = new Set();
    listeners.set(conversationId, subs);
  }
  subs.add(listener);
  return () => {
    const current = listeners.get(conversationId);
    current?.delete(listener);
    if (current && current.size === 0) {
      listeners.delete(conversationId);
    }
    prune(conversationId);
  };
}

export function beginLiveChatRun(
  conversationId: string,
  requestId: string,
  chatList: PersistedChatItem[],
): void {
  runs.set(conversationId, {
    conversationId,
    requestId,
    chatList,
    sendInFlight: true,
    userStopped: false,
    handle: null,
    skillAbort: null,
  });
  notify(conversationId);
}

export function patchLiveChatRun(
  conversationId: string,
  patch: Partial<
    Pick<LiveChatRun, 'chatList' | 'handle' | 'skillAbort' | 'sendInFlight'>
  >,
): void {
  const run = runs.get(conversationId);
  if (!run) {
    return;
  }
  if (patch.chatList) {
    run.chatList = patch.chatList;
  }
  if (patch.handle !== undefined) {
    run.handle = patch.handle;
  }
  if (patch.skillAbort !== undefined) {
    run.skillAbort = patch.skillAbort;
  }
  if (patch.sendInFlight !== undefined) {
    run.sendInFlight = patch.sendInFlight;
  }
  notify(conversationId);
}

export function applyUserStopToChatList(
  chatList: PersistedChatItem[],
  requestId: string,
): PersistedChatItem[] {
  return chatList.map((item) => {
    if (item.requestId !== requestId) {
      return item;
    }
    return {
      ...item,
      loading: false,
      forceStop: true,
      stoppedByUser: true,
      tip: '',
      response: USER_STOPPED_COPY,
      orchestration: item.orchestration
        ? markOrchestrationInterrupted(item.orchestration)
        : item.orchestration,
    };
  });
}

export function stopLiveChatRun(conversationId: string): boolean {
  const run = runs.get(conversationId);
  if (!run?.sendInFlight) {
    return false;
  }
  run.userStopped = true;
  if (run.requestId) {
    run.chatList = applyUserStopToChatList(run.chatList, run.requestId);
  }
  notify(conversationId);
  run.handle?.abort();
  run.skillAbort?.abort();
  return true;
}

export function finishLiveChatRun(
  conversationId: string,
  requestId: string,
): void {
  const run = runs.get(conversationId);
  if (!run || (run.requestId && run.requestId !== requestId)) {
    return;
  }
  run.sendInFlight = false;
  run.handle = null;
  run.skillAbort = null;
  notify(conversationId);
  prune(conversationId);
}

/** Test helper — not used by production UI. */
export function resetLiveChatRunsForTests(): void {
  runs.clear();
  listeners.clear();
}
