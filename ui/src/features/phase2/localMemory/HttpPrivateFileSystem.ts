import {
  deleteConversationSummary,
  deleteLongTermMemory,
  getConversationSummary,
  getLongTermMemory,
  getMemoryStatus,
  listMemorySummaries,
  putConversationSummary,
  putLongTermMemory,
} from '@/services/phase2/memory';
import {
  assertAllowedMemoryPath,
  buildConversationSummaryPath,
} from './paths';
import type { PrivateFileSystem } from './PrivateFileSystem';

function parseLongTermPath(path: string): { userId: string } | null {
  const match = /^\/memory\/v1\/users\/([^/]+)\/长期记忆\.md$/.exec(path);
  return match ? { userId: match[1] } : null;
}

function parseSummaryPath(
  path: string,
): { userId: string; conversationId: string } | null {
  const match =
    /^\/memory\/v1\/users\/([^/]+)\/conversations\/([^/]+)\/对话摘要\.md$/.exec(
      path,
    );
  return match
    ? {
        userId: match[1],
        conversationId: match[2]
      }
    : null;
}

export class HttpPrivateFileSystem implements PrivateFileSystem {
  async isAvailable(): Promise<boolean> {
    try {
      const status = await getMemoryStatus();
      return status?.available === true;
    } catch {
      return false;
    }
  }

  async readText(path: string): Promise<string | null> {
    assertAllowedMemoryPath(path);
    const longTerm = parseLongTermPath(path);
    if (longTerm) {
      const file = await getLongTermMemory();
      if (!file || file.status === 'EMPTY' || file.status === 'UNAVAILABLE') {
        return null;
      }
      return file.markdown;
    }
    const summary = parseSummaryPath(path);
    if (!summary) {
      throw new Error(`Disallowed memory path: ${path}`);
    }
    const file = await getConversationSummary(summary.conversationId);
    if (!file || file.status === 'EMPTY' || file.status === 'UNAVAILABLE') {
      return null;
    }
    return file.markdown;
  }

  async writeText(path: string, content: string): Promise<void> {
    assertAllowedMemoryPath(path);
    const longTerm = parseLongTermPath(path);
    if (longTerm) {
      await putLongTermMemory({ markdown: content });
      return;
    }
    const summary = parseSummaryPath(path);
    if (!summary) {
      throw new Error(`Disallowed memory path: ${path}`);
    }
    await putConversationSummary(summary.conversationId, { markdown: content });
  }

  async remove(path: string): Promise<void> {
    assertAllowedMemoryPath(path);
    const longTerm = parseLongTermPath(path);
    if (longTerm) {
      await deleteLongTermMemory();
      return;
    }
    const summary = parseSummaryPath(path);
    if (!summary) {
      throw new Error(`Disallowed memory path: ${path}`);
    }
    await deleteConversationSummary(summary.conversationId);
  }

  async listConversationSummaries(userId: string) {
    const listed = await listMemorySummaries();
    const items = listed?.items ?? [];
    const prefix = `/memory/v1/users/${userId}/conversations/`;
    return items
      .filter((item) => item.path.startsWith(prefix) || item.conversationId)
      .map((item) => ({
        path:
          item.path ||
          buildConversationSummaryPath(userId, item.conversationId),
        updatedAt: item.updatedAt,
        lastSummarizedTurnNo: item.lastSummarizedTurnNo,
      }));
  }

  async readRootPath(): Promise<string | null> {
    try {
      const status = await getMemoryStatus();
      return status?.rootPath ?? null;
    } catch {
      return null;
    }
  }
}
