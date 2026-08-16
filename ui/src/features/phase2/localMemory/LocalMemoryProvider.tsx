import {
  memo,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import type { ConversationMessageResponse } from '@/contracts';
import { getMessages } from '@/features/conversation/api';
import { FakeMemoryIndexStore } from './FakeMemoryIndexStore';
import { FakePrivateFileSystem } from './FakePrivateFileSystem';
import { IndexedDbMemoryIndexStore } from './IndexedDbMemoryIndexStore';
import { MemoryRepository } from './memoryRepository';
import { MemoryTaskQueue } from './memoryTaskQueue';
import {
  MemoryWorkflow,
  createSafeMemoryLogger,
} from './memoryWorkflow';
import { emptyLongTermMemoryDoc } from './markdownSerializer';
import { HttpPrivateFileSystem } from './HttpPrivateFileSystem';
import { migrateOpfsToDiskIfEmpty } from './opfsDiskMigration';
import type { PrivateFileSystem } from './PrivateFileSystem';
import type { MemoryIndexStore } from './MemoryIndexStore';
import {
  LocalMemoryContext,
  type LocalMemoryContextValue,
} from './useLocalMemory';
import type { OpfsStatus } from './types';

export type LocalMemoryProviderProps = {
  userId: string;
  children: ReactNode;
  /** Test-only injection. */
  fileSystem?: PrivateFileSystem;
  indexStore?: MemoryIndexStore;
  autoStart?: boolean;
};

type RuntimeBundle = {
  repository: MemoryRepository;
  queue: MemoryTaskQueue;
  workflow: MemoryWorkflow;
};

function createRuntime(
  userId: string,
  fileSystem: PrivateFileSystem,
  indexStore: MemoryIndexStore,
): RuntimeBundle {
  const repository = new MemoryRepository(userId, fileSystem, indexStore);
  const logger = createSafeMemoryLogger();
  const workflowRef: { current: MemoryWorkflow | null } = { current: null };

  const queue = new MemoryTaskQueue({
    userId,
    store: indexStore,
    executor: async (task) => {
      if (!workflowRef.current) {
        return;
      }
      await workflowRef.current.createExecutor()(task);
    },
    onLog: logger,
  });

  const workflow = new MemoryWorkflow({
    userId,
    repository,
    queue,
    getAuthUserId: () => userId,
    fetchMessages: async (conversationId, signal) => {
      const messages = await getMessages(conversationId);
      if (signal?.aborted) {
        throw new Error('aborted');
      }
      return messages ?? [];
    },
    onLog: logger,
  });
  workflowRef.current = workflow;

  return {
    repository,
    queue,
    workflow
  };
}

const LocalMemoryProvider: GenieType.FC<LocalMemoryProviderProps> = memo(
  (props) => {
    const {
      userId,
      children,
      fileSystem,
      indexStore,
      autoStart = true,
    } = props;

    const [opfsStatus, setOpfsStatus] = useState<OpfsStatus>('EMPTY');
    const [diskRootPath, setDiskRootPath] = useState<string | null>(null);
    const bundleRef = useRef<RuntimeBundle | null>(null);
    const [bundleVersion, setBundleVersion] = useState(0);

    useEffect(() => {
      const fs = fileSystem ?? new HttpPrivateFileSystem();
      const store = indexStore ?? new IndexedDbMemoryIndexStore();
      const bundle = createRuntime(userId, fs, store);
      bundleRef.current = bundle;
      setBundleVersion((v) => v + 1);

      let cancelled = false;
      (async () => {
        const available = await fs.isAvailable();
        if (cancelled) {
          return;
        }
        if (!available) {
          setOpfsStatus('UNAVAILABLE');
          bundle.queue.pauseForUnavailable();
          return;
        }
        if (!fileSystem) {
          try {
            await migrateOpfsToDiskIfEmpty(userId, fs);
          } catch {
            // Migration is best-effort; disk remains the authority.
          }
        }
        if (fs instanceof HttpPrivateFileSystem) {
          setDiskRootPath(await fs.readRootPath());
        }
        setOpfsStatus('READY');
        if (autoStart) {
          bundle.queue.start();
        }
      })();

      return () => {
        cancelled = true;
        bundle.queue.stop();
        bundle.workflow.abort();
        bundleRef.current = null;
        setOpfsStatus('EMPTY');
        setDiskRootPath(null);
      };
    }, [userId, fileSystem, indexStore, autoStart]);

    const refreshStatus = useCallback(async () => {
      const bundle = bundleRef.current;
      if (!bundle) {
        return;
      }
      const status = await bundle.repository.getOpfsStatus();
      if (status === 'UNAVAILABLE') {
        setOpfsStatus('UNAVAILABLE');
        bundle.queue.pauseForUnavailable();
        return;
      }
      const ltm = await bundle.repository.readLongTermMemory();
      if (ltm.status === 'CORRUPTED') {
        setOpfsStatus('CORRUPTED');
        return;
      }
      if (ltm.status === 'ERROR') {
        setOpfsStatus('ERROR');
        return;
      }
      if (ltm.status === 'EMPTY') {
        setOpfsStatus('EMPTY');
        bundle.queue.resumeFromUnavailable();
        return;
      }
      setOpfsStatus('READY');
      bundle.queue.resumeFromUnavailable();
    }, [bundleVersion]);

    const observeCompletedMessages = useCallback(
      async (
        conversationId: string,
        messages: ConversationMessageResponse[],
      ) => {
        const bundle = bundleRef.current;
        if (!bundle) {
          return;
        }
        await bundle.workflow.observeCompletedMessages(
          userId,
          conversationId,
          messages,
        );
      },
      [userId, bundleVersion],
    );

    const listSummaryIndex = useCallback(async () => {
      const bundle = bundleRef.current;
      if (!bundle) {
        return [];
      }
      return bundle.repository.listSummaryIndex();
    }, [bundleVersion]);

    const listTasks = useCallback(async () => {
      const bundle = bundleRef.current;
      if (!bundle) {
        return [];
      }
      return bundle.queue.listTasks();
    }, [bundleVersion]);

    const retryFailedTasks = useCallback(
      async (conversationId?: string) => {
        const bundle = bundleRef.current;
        if (!bundle) {
          return;
        }
        await bundle.queue.retryFailed(conversationId);
      },
      [bundleVersion],
    );

    const rebuildLongTermMemory = useCallback(async () => {
      const bundle = bundleRef.current;
      if (!bundle) {
        return;
      }
      await bundle.repository.writeLongTermMemory(emptyLongTermMemoryDoc());
      await refreshStatus();
    }, [bundleVersion, refreshStatus]);

    const rebuildConversationSummary = useCallback(
      async (
        conversationId: string,
        messages: ConversationMessageResponse[],
      ) => {
        const bundle = bundleRef.current;
        if (!bundle) {
          return;
        }
        await bundle.workflow.requestSummarizeRebuild(
          conversationId,
          messages,
        );
      },
      [bundleVersion],
    );

    const clearLongTermMemory = useCallback(async () => {
      const bundle = bundleRef.current;
      if (!bundle) {
        return;
      }
      await bundle.repository.clearLongTermMemory();
      await refreshStatus();
    }, [bundleVersion, refreshStatus]);

    const clearConversationSummary = useCallback(
      async (conversationId: string) => {
        const bundle = bundleRef.current;
        if (!bundle) {
          return;
        }
        await bundle.repository.clearConversationSummary(conversationId);
      },
      [bundleVersion],
    );

    const exportTextFile = useCallback((filename: string, content: string) => {
      const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      try {
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = filename;
        anchor.click();
      } finally {
        URL.revokeObjectURL(url);
      }
    }, []);

    const value = useMemo<LocalMemoryContextValue>(() => {
      const bundle = bundleRef.current;
      return {
        userId,
        opfsStatus,
        diskRootPath,
        repository: bundle?.repository ?? null,
        queue: bundle?.queue ?? null,
        workflow: bundle?.workflow ?? null,
        refreshStatus,
        observeCompletedMessages,
        listSummaryIndex,
        listTasks,
        retryFailedTasks,
        rebuildLongTermMemory,
        rebuildConversationSummary,
        clearLongTermMemory,
        clearConversationSummary,
        exportTextFile,
      };
    }, [
      userId,
      opfsStatus,
      diskRootPath,
      refreshStatus,
      observeCompletedMessages,
      listSummaryIndex,
      listTasks,
      retryFailedTasks,
      rebuildLongTermMemory,
      rebuildConversationSummary,
      clearLongTermMemory,
      clearConversationSummary,
      exportTextFile,
      bundleVersion,
    ]);

    return (
      <LocalMemoryContext.Provider value={value}>
        {children}
      </LocalMemoryContext.Provider>
    );
  },
);

export default LocalMemoryProvider;

/** Helpers for unit tests that need Fake adapters without React. */
export function createTestLocalMemory(
  userId: string,
  options?: {
    available?: boolean;
    readBackMismatch?: boolean;
    writeError?: string | null;
  },
) {
  const fs = new FakePrivateFileSystem({
    available: options?.available,
    readBackMismatch: options?.readBackMismatch,
    writeError: options?.writeError,
  });
  const store = new FakeMemoryIndexStore();
  return {
    ...createRuntime(userId, fs, store),
    fs,
    store
  };
}
