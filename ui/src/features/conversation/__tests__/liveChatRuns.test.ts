import { describe, expect, it, vi, beforeEach } from 'vitest';
import {
  beginLiveChatRun,
  finishLiveChatRun,
  peekLiveChatRun,
  resetLiveChatRunsForTests,
  stopLiveChatRun,
  subscribeLiveChatRun,
  USER_STOPPED_COPY,
  patchLiveChatRun,
} from '../liveChatRuns';
import type { PersistedChatItem } from '../types';
import { createInitialOrchestrationState } from '@/features/phase2/orchestration/orchestrationReducer';

function chat(partial: Partial<PersistedChatItem> & Pick<PersistedChatItem, 'requestId'>): PersistedChatItem {
  return {
    query: 'q',
    files: [],
    responseType: 'txt',
    sessionId: 'conv-1',
    loading: true,
    forceStop: false,
    tasks: [],
    thought: '',
    response: '',
    taskStatus: 0,
    multiAgent: { tasks: [] },
    deepThink: false,
    outputStyle: 'docs',
    persistedStatus: 'STREAMING',
    orchestration: {
      ...createInitialOrchestrationState(),
      route: 'ORCHESTRATED',
      masterOpen: true,
      main: { open: true, lines: [] },
    },
    ...partial,
  };
}

describe('liveChatRuns', () => {
  beforeEach(() => {
    resetLiveChatRunsForTests();
  });

  it('keeps a run after unsubscribe so navigation does not drop it', () => {
    beginLiveChatRun('conv-1', 'req-1', [chat({ requestId: 'req-1' })]);
    const listener = vi.fn();
    const unsub = subscribeLiveChatRun('conv-1', listener);
    unsub();
    expect(peekLiveChatRun('conv-1')?.sendInFlight).toBe(true);
    expect(peekLiveChatRun('conv-1')?.requestId).toBe('req-1');
  });

  it('stop collapses folds, writes copy, and aborts the handle', () => {
    beginLiveChatRun('conv-1', 'req-1', [chat({ requestId: 'req-1' })]);
    const abort = vi.fn();
    patchLiveChatRun('conv-1', { handle: { abort, done: Promise.resolve({ kind: 'INTERRUPTED', reason: 'ABORT' }) } });
    const snapshots: string[] = [];
    subscribeLiveChatRun('conv-1', (snap) => {
      snapshots.push(snap.chatList[0]?.response ?? '');
    });
    expect(stopLiveChatRun('conv-1')).toBe(true);
    expect(abort).toHaveBeenCalledTimes(1);
    const run = peekLiveChatRun('conv-1');
    expect(run?.userStopped).toBe(true);
    expect(run?.chatList[0].response).toBe(USER_STOPPED_COPY);
    expect(run?.chatList[0].stoppedByUser).toBe(true);
    expect(run?.chatList[0].orchestration?.masterOpen).toBe(false);
    expect(run?.chatList[0].orchestration?.terminalStatus).toBe('INTERRUPTED');
    expect(snapshots[snapshots.length - 1]).toBe(USER_STOPPED_COPY);
  });

  it('finish removes the in-flight run', () => {
    beginLiveChatRun('conv-1', 'req-1', [chat({ requestId: 'req-1' })]);
    finishLiveChatRun('conv-1', 'req-1');
    expect(peekLiveChatRun('conv-1')).toBeNull();
  });
});
