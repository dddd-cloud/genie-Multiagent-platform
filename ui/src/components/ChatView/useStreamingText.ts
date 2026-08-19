import { useEffect, useRef } from 'react';
import {
  patchLiveChatRun,
  peekLiveChatRun,
} from '@/features/conversation/liveChatRuns';
import type { PersistedChatItem } from '@/features/conversation/types';

/**
 * SSE reduction stays synchronous (`patchLiveChatRun` + chatListRef).
 * Only the React mirror (`setChatList`) is rAF-batched; terminal events must flush.
 */
export function useStreamingText(options: {
  sessionId: string;
  mountedRef: React.MutableRefObject<boolean>;
  chatListRef: React.MutableRefObject<PersistedChatItem[]>;
  setChatList: (next: PersistedChatItem[]) => void;
}) {
  const { sessionId, mountedRef, chatListRef, setChatList } = options;
  const rafRef = useRef<number | null>(null);

  const flushStreamingView = () => {
    if (rafRef.current != null) {
      cancelAnimationFrame(rafRef.current);
      rafRef.current = null;
    }
    if (mountedRef.current) {
      setChatList(chatListRef.current);
    }
  };

  const publishChatList = (
    updater: (prev: PersistedChatItem[]) => PersistedChatItem[],
  ) => {
    const prev = peekLiveChatRun(sessionId)?.chatList ?? chatListRef.current;
    const next = updater(prev);
    chatListRef.current = next;
    patchLiveChatRun(sessionId, { chatList: next });
    if (rafRef.current == null && typeof requestAnimationFrame === 'function') {
      rafRef.current = requestAnimationFrame(() => {
        rafRef.current = null;
        if (mountedRef.current) {
          setChatList(chatListRef.current);
        }
      });
    } else if (rafRef.current == null) {
      if (mountedRef.current) setChatList(next);
    }
    return next;
  };

  useEffect(
    () => () => {
      if (rafRef.current != null) {
        cancelAnimationFrame(rafRef.current);
      }
    },
    [],
  );

  return { publishChatList, flushStreamingView };
}
