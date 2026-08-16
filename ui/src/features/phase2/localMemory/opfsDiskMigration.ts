import { OpfsPrivateFileSystem } from './OpfsPrivateFileSystem';
import {
  buildConversationSummaryPath,
  buildLongTermMemoryPath,
} from './paths';
import type { PrivateFileSystem } from './PrivateFileSystem';

/**
 * One-shot copy of leftover browser OPFS files onto the backend disk store
 * when the disk copy is still empty.
 */
export async function migrateOpfsToDiskIfEmpty(
  userId: string,
  disk: PrivateFileSystem,
): Promise<void> {
  const opfs = new OpfsPrivateFileSystem();
  if (!(await opfs.isAvailable()) || !(await disk.isAvailable())) {
    return;
  }

  const longTermPath = buildLongTermMemoryPath(userId);
  const existing = await disk.readText(longTermPath);
  if (existing == null) {
    const legacy = await opfs.readText(longTermPath);
    if (legacy != null && legacy.length > 0) {
      await disk.writeText(longTermPath, legacy);
    }
  }

  const summaries = await opfs.listConversationSummaries(userId);
  for (const item of summaries) {
    const match = /\/conversations\/([^/]+)\//.exec(item.path);
    const conversationId = match?.[1];
    if (!conversationId) {
      continue;
    }
    const path = buildConversationSummaryPath(userId, conversationId);
    const current = await disk.readText(path);
    if (current != null) {
      continue;
    }
    const legacy = await opfs.readText(path);
    if (legacy != null && legacy.length > 0) {
      await disk.writeText(path, legacy);
    }
  }
}
