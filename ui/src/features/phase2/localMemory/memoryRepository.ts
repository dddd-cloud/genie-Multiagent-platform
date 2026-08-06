import type { MemoryPatchItem } from '@/contracts/phase2';
import type { MemoryIndexStore } from './MemoryIndexStore';
import {
  parseConversationSummary,
  parseLongTermMemory,
  type ParseResult,
} from './markdownParser';
import {
  emptyLongTermMemoryDoc,
  serializeConversationSummary,
  serializeLongTermMemory,
} from './markdownSerializer';
import {
  buildConversationSummaryPath,
  buildLongTermMemoryPath,
} from './paths';
import type { PrivateFileSystem } from './PrivateFileSystem';
import {
  MEMORY_LIMITS,
  MemoryError,
  codePointLength,
  type ConversationSummaryDoc,
  type LongTermMemoryDoc,
  type OpfsStatus,
} from './types';

export type ReadLongTermResult =
  | { status: 'READY'; doc: LongTermMemoryDoc; raw: string }
  | { status: 'EMPTY'; doc: LongTermMemoryDoc; raw: null }
  | { status: 'CORRUPTED'; reason: string; raw: string }
  | { status: 'UNAVAILABLE' }
  | { status: 'ERROR'; errorCode: string; message: string };

export type ReadSummaryResult =
  | { status: 'READY'; doc: ConversationSummaryDoc; raw: string }
  | { status: 'EMPTY'; raw: null }
  | { status: 'CORRUPTED'; reason: string; raw: string }
  | { status: 'UNAVAILABLE' }
  | { status: 'ERROR'; errorCode: string; message: string };

export class MemoryRepository {
  constructor(
    private readonly userId: string,
    private readonly fs: PrivateFileSystem,
    private readonly index: MemoryIndexStore,
  ) {}

  getUserId(): string {
    return this.userId;
  }

  async getOpfsStatus(): Promise<OpfsStatus> {
    const available = await this.fs.isAvailable();
    if (!available) {
      return 'UNAVAILABLE';
    }
    return 'READY';
  }

  async readLongTermMemory(): Promise<ReadLongTermResult> {
    if (!(await this.fs.isAvailable())) {
      return { status: 'UNAVAILABLE' };
    }
    const path = buildLongTermMemoryPath(this.userId);
    try {
      const raw = await this.fs.readText(path);
      if (raw == null) {
        return {
          status: 'EMPTY',
          doc: emptyLongTermMemoryDoc(),
          raw: null
        };
      }
      const parsed: ParseResult<LongTermMemoryDoc> = parseLongTermMemory(raw);
      if (!parsed.ok) {
        return {
          status: 'CORRUPTED',
          reason: parsed.reason,
          raw
        };
      }
      return {
        status: 'READY',
        doc: parsed.doc,
        raw
      };
    } catch (error) {
      return {
        status: 'ERROR',
        errorCode: 'OPFS_WRITE_FAILED',
        message: error instanceof Error ? error.message : 'read failed',
      };
    }
  }

  async readConversationSummary(
    conversationId: string,
  ): Promise<ReadSummaryResult> {
    if (!(await this.fs.isAvailable())) {
      return { status: 'UNAVAILABLE' };
    }
    const path = buildConversationSummaryPath(this.userId, conversationId);
    try {
      const raw = await this.fs.readText(path);
      if (raw == null) {
        return {
          status: 'EMPTY',
          raw: null
        };
      }
      const parsed = parseConversationSummary(raw);
      if (!parsed.ok) {
        return {
          status: 'CORRUPTED',
          reason: parsed.reason,
          raw
        };
      }
      return {
        status: 'READY',
        doc: parsed.doc,
        raw
      };
    } catch (error) {
      return {
        status: 'ERROR',
        errorCode: 'OPFS_WRITE_FAILED',
        message: error instanceof Error ? error.message : 'read failed',
      };
    }
  }

  applyPatches(
    doc: LongTermMemoryDoc,
    patches: MemoryPatchItem[],
  ): LongTermMemoryDoc {
    const next: LongTermMemoryDoc = {
      schemaVersion: 1,
      updatedAt: new Date().toISOString(),
      sections: {
        基本信息: [...doc.sections.基本信息],
        回答偏好: [...doc.sections.回答偏好],
        长期目标: [...doc.sections.长期目标],
        长期约束: [...doc.sections.长期约束],
      },
    };

    for (const patch of patches) {
      const list = next.sections[patch.section];
      const idx = list.findIndex((entry) => entry.key === patch.key);
      if (patch.operation === 'DELETE') {
        if (idx >= 0) {
          list.splice(idx, 1);
        }
        continue;
      }
      if (idx >= 0) {
        list[idx] = {
          key: patch.key,
          value: patch.value
        };
      } else {
        list.push({
          key: patch.key,
          value: patch.value
        });
      }
    }
    return next;
  }

  async writeLongTermMemory(doc: LongTermMemoryDoc): Promise<void> {
    if (!(await this.fs.isAvailable())) {
      throw new MemoryError('OPFS_UNAVAILABLE', 'OPFS unavailable', false);
    }
    const path = buildLongTermMemoryPath(this.userId);
    const content = serializeLongTermMemory(doc);
    if (codePointLength(content) > MEMORY_LIMITS.LTM_MAX_CODEPOINTS) {
      throw new MemoryError(
        'MEMORY_VALIDATION_FAILED',
        'long-term memory too large',
        false,
      );
    }
    await this.writeVerified(path, content);
    await this.index.putIndex({
      userId: this.userId,
      path,
      schemaVersion: 1,
      updatedAt: doc.updatedAt,
      lastSummarizedTurnNo: null,
    });
  }

  async writeConversationSummary(doc: ConversationSummaryDoc): Promise<void> {
    if (!(await this.fs.isAvailable())) {
      throw new MemoryError('OPFS_UNAVAILABLE', 'OPFS unavailable', false);
    }
    const path = buildConversationSummaryPath(
      this.userId,
      doc.conversationId,
    );
    const content = serializeConversationSummary(doc);
    if (codePointLength(content) > MEMORY_LIMITS.SUMMARY_MAX_CODEPOINTS) {
      throw new MemoryError(
        'MEMORY_VALIDATION_FAILED',
        'summary too large',
        false,
      );
    }
    await this.writeVerified(path, content);
    await this.index.putIndex({
      userId: this.userId,
      path,
      schemaVersion: 1,
      updatedAt: doc.updatedAt,
      lastSummarizedTurnNo: doc.lastSummarizedTurnNo,
    });
  }

  async clearLongTermMemory(): Promise<void> {
    const path = buildLongTermMemoryPath(this.userId);
    if (await this.fs.isAvailable()) {
      await this.fs.remove(path);
    }
    await this.index.deleteIndex(this.userId, path);
  }

  async clearConversationSummary(conversationId: string): Promise<void> {
    const path = buildConversationSummaryPath(this.userId, conversationId);
    if (await this.fs.isAvailable()) {
      await this.fs.remove(path);
    }
    await this.index.deleteIndex(this.userId, path);
  }

  async listSummaryIndex() {
    const records = await this.index.listIndex(this.userId);
    return records.filter((record) =>
      record.path.includes('/conversations/'),
    );
  }

  private async writeVerified(path: string, content: string): Promise<void> {
    try {
      await this.fs.writeText(path, content);
    } catch (error) {
      throw new MemoryError(
        'OPFS_WRITE_FAILED',
        error instanceof Error ? error.message : 'write failed',
        true,
      );
    }
    let readBack: string | null;
    try {
      readBack = await this.fs.readText(path);
    } catch (error) {
      throw new MemoryError(
        'OPFS_WRITE_FAILED',
        error instanceof Error ? error.message : 'read-back failed',
        true,
      );
    }
    if (readBack !== content) {
      throw new MemoryError(
        'OPFS_WRITE_MISMATCH',
        'OPFS read-back mismatch',
        true,
      );
    }
  }
}
