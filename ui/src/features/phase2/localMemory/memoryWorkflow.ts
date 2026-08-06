import type { ConversationMessageResponse } from '@/contracts';
import {
  analyzeTurn,
  summarizeConversation,
} from '@/services/phase2/memory';
import { MvpApiError } from '@/services';
import { parseSummarySectionsFromMarkdown } from './markdownParser';
import { assertValidMemoryPatches } from './memoryPatchValidator';
import type { MemoryRepository } from './memoryRepository';
import type { MemoryTaskQueue } from './memoryTaskQueue';
import {
  MEMORY_LIMITS,
  MemoryError,
  codePointLength,
  type ConversationSummaryDoc,
  type MemoryTaskRecord,
} from './types';

export type MemoryWorkflowDeps = {
  userId: string;
  repository: MemoryRepository;
  queue: MemoryTaskQueue;
  getAuthUserId: () => string | null;
  fetchMessages: (
    conversationId: string,
    signal?: AbortSignal,
  ) => Promise<ConversationMessageResponse[]>;
  onLog?: (info: {
    type: MemoryTaskRecord['type'];
    conversationId: string;
    requestId: string;
    errorCode?: string;
  }) => void;
};

function groupTurns(messages: ConversationMessageResponse[]): Array<{
  requestId: string;
  turnNo: number;
  user: ConversationMessageResponse;
  assistant: ConversationMessageResponse;
}> {
  const byRequest = new Map<
    string,
    { user?: ConversationMessageResponse; assistant?: ConversationMessageResponse }
  >();
  for (const msg of messages) {
    const bucket = byRequest.get(msg.requestId) ?? {};
    if (msg.role === 'USER') {
      bucket.user = msg;
    } else if (msg.role === 'ASSISTANT') {
      bucket.assistant = msg;
    }
    byRequest.set(msg.requestId, bucket);
  }

  const turns: Array<{
    requestId: string;
    turnNo: number;
    user: ConversationMessageResponse;
    assistant: ConversationMessageResponse;
  }> = [];

  for (const [requestId, pair] of byRequest) {
    if (
      pair.user &&
      pair.assistant &&
      pair.assistant.status === 'COMPLETED'
    ) {
      turns.push({
        requestId,
        turnNo: pair.assistant.turnNo,
        user: pair.user,
        assistant: pair.assistant,
      });
    }
  }

  turns.sort((a, b) => a.turnNo - b.turnNo);
  return turns;
}

function isRetryableHttp(error: unknown): boolean {
  if (error instanceof MvpApiError) {
    if (
      error.code === 'MEMORY_ANALYSIS_FAILED' ||
      error.code === 'SUMMARY_FAILED' ||
      error.code === 'VALIDATION_ERROR'
    ) {
      return false;
    }
    return error.httpStatus === 0 || error.httpStatus >= 500;
  }
  return true;
}

export class MemoryWorkflow {
  private readonly userId: string;
  private readonly repository: MemoryRepository;
  private readonly queue: MemoryTaskQueue;
  private readonly getAuthUserId: () => string | null;
  private readonly fetchMessages: MemoryWorkflowDeps['fetchMessages'];
  private readonly onLog?: MemoryWorkflowDeps['onLog'];
  private abortController: AbortController | null = null;
  private securityDiscardCount = 0;

  constructor(deps: MemoryWorkflowDeps) {
    this.userId = deps.userId;
    this.repository = deps.repository;
    this.queue = deps.queue;
    this.getAuthUserId = deps.getAuthUserId;
    this.fetchMessages = deps.fetchMessages;
    this.onLog = deps.onLog;
  }

  getSecurityDiscardCount(): number {
    return this.securityDiscardCount;
  }

  abort(): void {
    this.abortController?.abort();
    this.abortController = null;
  }

  createExecutor(): (task: MemoryTaskRecord) => Promise<void> {
    return async (task) => this.execute(task);
  }

  async observeCompletedMessages(
    userId: string,
    conversationId: string,
    messages: ConversationMessageResponse[],
    options?: { forceSummarize?: boolean },
  ): Promise<void> {
    if (userId !== this.userId) {
      return;
    }
    if ((await this.repository.getOpfsStatus()) === 'UNAVAILABLE') {
      this.queue.pauseForUnavailable();
      return;
    }

    const turns = groupTurns(messages);
    for (const turn of turns) {
      await this.queue.enqueue({
        conversationId,
        requestId: turn.requestId,
        type: 'ANALYZE_TURN',
      });
    }

    const shouldSummarize = await this.shouldEnqueueSummarize(
      conversationId,
      turns,
      options?.forceSummarize === true,
    );
    if (shouldSummarize && turns.length > 0) {
      const latest = turns[turns.length - 1];
      await this.queue.enqueue({
        conversationId,
        requestId: latest.requestId,
        type: 'SUMMARIZE_CONVERSATION',
      });
    }
  }

  async requestSummarizeRebuild(
    conversationId: string,
    messages: ConversationMessageResponse[],
  ): Promise<void> {
    await this.observeCompletedMessages(this.userId, conversationId, messages, {forceSummarize: true,});
  }

  private async shouldEnqueueSummarize(
    conversationId: string,
    turns: Array<{ turnNo: number }>,
    force: boolean,
  ): Promise<boolean> {
    if (force) {
      return true;
    }
    if (turns.length === 0) {
      return false;
    }

    const summary = await this.repository.readConversationSummary(conversationId);
    if (summary.status === 'UNAVAILABLE') {
      this.queue.pauseForUnavailable();
      return false;
    }
    if (summary.status === 'EMPTY' || summary.status === 'CORRUPTED') {
      return true;
    }
    if (summary.status !== 'READY') {
      return false;
    }

    const last = summary.doc.lastSummarizedTurnNo;
    const maxTurn = turns[turns.length - 1].turnNo;
    if (maxTurn - last >= MEMORY_LIMITS.SUMMARIZE_TURN_DELTA) {
      return true;
    }

    const ltm = await this.repository.readLongTermMemory();
    const ltmText =
      ltm.status === 'READY' || ltm.status === 'EMPTY'
        ? (ltm.raw ?? '')
        : '';
    const summaryText = summary.raw ?? '';
    if (
      codePointLength(ltmText) + codePointLength(summaryText) >=
      MEMORY_LIMITS.LOCAL_CONTEXT_WARN_CODEPOINTS
    ) {
      return true;
    }
    return false;
  }

  private async execute(task: MemoryTaskRecord): Promise<void> {
    const authUserId = this.getAuthUserId();
    if (!authUserId || authUserId !== task.userId || task.userId !== this.userId) {
      this.securityDiscardCount += 1;
      this.onLog?.({
        type: task.type,
        conversationId: task.conversationId,
        requestId: task.requestId,
        errorCode: 'MEMORY_ACCOUNT_MISMATCH',
      });
      throw new MemoryError(
        'MEMORY_ACCOUNT_MISMATCH',
        'account mismatch',
        false,
      );
    }

    if ((await this.repository.getOpfsStatus()) === 'UNAVAILABLE') {
      this.queue.pauseForUnavailable();
      throw new MemoryError('OPFS_UNAVAILABLE', 'OPFS unavailable', false);
    }

    this.abortController?.abort();
    this.abortController = new AbortController();
    const signal = this.abortController.signal;

    let messages: ConversationMessageResponse[];
    try {
      messages = await this.fetchMessages(task.conversationId, signal);
    } catch (error) {
      if (error instanceof MemoryError) {
        throw error;
      }
      throw new MemoryError(
        'MEMORY_RETRYABLE',
        error instanceof Error ? error.message : 'fetch messages failed',
        isRetryableHttp(error),
      );
    }

    const turns = groupTurns(messages);
    const turn = turns.find((item) => item.requestId === task.requestId);
    if (!turn) {
      this.securityDiscardCount += 1;
      this.onLog?.({
        type: task.type,
        conversationId: task.conversationId,
        requestId: task.requestId,
        errorCode: 'MEMORY_TASK_DISCARDED',
      });
      throw new MemoryError(
        'MEMORY_TASK_DISCARDED',
        'completed assistant turn not found',
        false,
      );
    }

    if (task.type === 'ANALYZE_TURN') {
      await this.runAnalyzeTurn(task, turn, signal);
      return;
    }
    await this.runSummarize(task, turns, signal);
  }

  private async runAnalyzeTurn(
    task: MemoryTaskRecord,
    turn: {
      user: ConversationMessageResponse;
      assistant: ConversationMessageResponse;
    },
    signal: AbortSignal,
  ): Promise<void> {
    const current = await this.repository.readLongTermMemory();
    if (current.status === 'CORRUPTED') {
      throw new MemoryError('OPFS_CORRUPTED', current.reason, false);
    }
    if (current.status === 'UNAVAILABLE') {
      this.queue.pauseForUnavailable();
      throw new MemoryError('OPFS_UNAVAILABLE', 'OPFS unavailable', false);
    }
    if (current.status === 'ERROR') {
      throw new MemoryError('OPFS_WRITE_FAILED', current.message, true);
    }

    const currentMemory =
      current.status === 'READY' ? (current.raw ?? '') : '';

    let response;
    try {
      response = await analyzeTurn(
        {
          conversationId: task.conversationId,
          userMessage: turn.user.content ?? '',
          assistantMessage: turn.assistant.content ?? '',
          currentLongTermMemory: currentMemory,
          turnStatus: 'COMPLETED',
        },
        signal,
      );
    } catch (error) {
      if (error instanceof MvpApiError) {
        throw new MemoryError(
          error.code === 'MEMORY_ANALYSIS_FAILED'
            ? 'MEMORY_ANALYSIS_FAILED'
            : 'MEMORY_RETRYABLE',
          error.message,
          isRetryableHttp(error),
        );
      }
      throw new MemoryError(
        'MEMORY_RETRYABLE',
        error instanceof Error ? error.message : 'analyze failed',
        true,
      );
    }

    if (!response || response.schemaVersion !== 1) {
      throw new MemoryError(
        'MEMORY_VALIDATION_FAILED',
        'invalid analyze response',
        false,
      );
    }

    const patches = assertValidMemoryPatches(response.patches);
    const next = this.repository.applyPatches(current.doc, patches);
    await this.repository.writeLongTermMemory(next);
    this.onLog?.({
      type: task.type,
      conversationId: task.conversationId,
      requestId: task.requestId,
    });
  }

  private async runSummarize(
    task: MemoryTaskRecord,
    turns: Array<{
      requestId: string;
      turnNo: number;
      user: ConversationMessageResponse;
      assistant: ConversationMessageResponse;
    }>,
    signal: AbortSignal,
  ): Promise<void> {
    const current = await this.repository.readConversationSummary(
      task.conversationId,
    );
    if (current.status === 'CORRUPTED') {
      // Explicit rebuild path may overwrite; automatic path stops.
      throw new MemoryError('OPFS_CORRUPTED', current.reason, false);
    }
    if (current.status === 'UNAVAILABLE') {
      this.queue.pauseForUnavailable();
      throw new MemoryError('OPFS_UNAVAILABLE', 'OPFS unavailable', false);
    }
    if (current.status === 'ERROR') {
      throw new MemoryError('OPFS_WRITE_FAILED', current.message, true);
    }

    const lastSummarized =
      current.status === 'READY' ? current.doc.lastSummarizedTurnNo : 0;
    const newTurns = turns.filter((turn) => turn.turnNo > lastSummarized);
    if (newTurns.length === 0 && current.status === 'READY') {
      return;
    }

    const payloadTurns = (newTurns.length > 0 ? newTurns : turns).map(
      (turn) => ({
        turnNo: turn.turnNo,
        userMessage: turn.user.content ?? '',
        assistantMessage: turn.assistant.content ?? '',
        assistantStatus: turn.assistant.status,
      }),
    );

    let response;
    try {
      response = await summarizeConversation(
        {
          conversationId: task.conversationId,
          currentSummary: current.status === 'READY' ? (current.raw ?? '') : '',
          newTurns: payloadTurns,
        },
        signal,
      );
    } catch (error) {
      if (error instanceof MvpApiError) {
        throw new MemoryError(
          error.code === 'SUMMARY_FAILED' ? 'SUMMARY_FAILED' : 'MEMORY_RETRYABLE',
          error.message,
          isRetryableHttp(error),
        );
      }
      throw new MemoryError(
        'MEMORY_RETRYABLE',
        error instanceof Error ? error.message : 'summarize failed',
        true,
      );
    }

    if (!response || response.schemaVersion !== 1 || !response.markdown) {
      throw new MemoryError(
        'MEMORY_VALIDATION_FAILED',
        'invalid summarize response',
        false,
      );
    }

    const sectionsResult = parseSummarySectionsFromMarkdown(response.markdown);
    if (!sectionsResult.ok) {
      throw new MemoryError(
        'MEMORY_VALIDATION_FAILED',
        sectionsResult.reason,
        false,
      );
    }

    const maxTurn = turns[turns.length - 1]?.turnNo ?? lastSummarized;
    const doc: ConversationSummaryDoc = {
      schemaVersion: 1,
      conversationId: task.conversationId,
      lastSummarizedTurnNo: maxTurn,
      updatedAt: new Date().toISOString(),
      sections: sectionsResult.doc,
    };
    await this.repository.writeConversationSummary(doc);
    this.onLog?.({
      type: task.type,
      conversationId: task.conversationId,
      requestId: task.requestId,
    });
  }
}

export function createSafeMemoryLogger(): MemoryWorkflowDeps['onLog'] {
  return (info) => {
    // Never log markdown/content — IDs and codes only.
    console.info('[localMemory]', {
      type: info.type,
      conversationId: info.conversationId,
      requestId: info.requestId,
      errorCode: info.errorCode,
    });
  };
}
