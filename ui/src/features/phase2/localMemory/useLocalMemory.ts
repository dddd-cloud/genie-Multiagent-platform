import { createContext, useContext } from 'react';
import type { ConversationMessageResponse } from '@/contracts';
import type { MemoryIndexRecord, MemoryTaskRecord, OpfsStatus } from './types';
import type { MemoryRepository } from './memoryRepository';
import type { MemoryTaskQueue } from './memoryTaskQueue';
import type { MemoryWorkflow } from './memoryWorkflow';

export type LocalMemoryContextValue = {
  userId: string;
  opfsStatus: OpfsStatus;
  diskRootPath: string | null;
  repository: MemoryRepository | null;
  queue: MemoryTaskQueue | null;
  workflow: MemoryWorkflow | null;
  refreshStatus: () => Promise<void>;
  observeCompletedMessages: (
    conversationId: string,
    messages: ConversationMessageResponse[],
  ) => Promise<void>;
  listSummaryIndex: () => Promise<MemoryIndexRecord[]>;
  listTasks: () => Promise<MemoryTaskRecord[]>;
  retryFailedTasks: (conversationId?: string) => Promise<void>;
  rebuildLongTermMemory: () => Promise<void>;
  rebuildConversationSummary: (
    conversationId: string,
    messages: ConversationMessageResponse[],
  ) => Promise<void>;
  clearLongTermMemory: () => Promise<void>;
  clearConversationSummary: (conversationId: string) => Promise<void>;
  exportTextFile: (filename: string, content: string) => void;
};

export const LocalMemoryContext =
  createContext<LocalMemoryContextValue | null>(null);

export function useLocalMemory(): LocalMemoryContextValue {
  const ctx = useContext(LocalMemoryContext);
  if (!ctx) {
    throw new Error('useLocalMemory must be used within LocalMemoryProvider');
  }
  return ctx;
}

export function useLocalMemoryOptional(): LocalMemoryContextValue | null {
  return useContext(LocalMemoryContext);
}
