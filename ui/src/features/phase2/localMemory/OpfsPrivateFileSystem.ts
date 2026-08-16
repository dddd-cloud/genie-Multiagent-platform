import { assertAllowedMemoryPath } from './paths';
import type { PrivateFileSystem } from './PrivateFileSystem';

function splitPath(path: string): string[] {
  assertAllowedMemoryPath(path);
  return path.split('/').filter((part) => part.length > 0);
}

async function resolveParentDirectory(
  root: FileSystemDirectoryHandle,
  segments: string[],
  create: boolean,
): Promise<{ parent: FileSystemDirectoryHandle; fileName: string }> {
  if (segments.length === 0) {
    throw new Error('Invalid OPFS path');
  }
  let current = root;
  for (let i = 0; i < segments.length - 1; i += 1) {
    current = await current.getDirectoryHandle(segments[i], { create });
  }
  return {
    parent: current,
    fileName: segments[segments.length - 1]
  };
}

export class OpfsPrivateFileSystem implements PrivateFileSystem {
  async isAvailable(): Promise<boolean> {
    try {
      if (
        typeof navigator === 'undefined' ||
        !navigator.storage ||
        typeof navigator.storage.getDirectory !== 'function'
      ) {
        return false;
      }
      await navigator.storage.getDirectory();
      return true;
    } catch {
      return false;
    }
  }

  private async root(): Promise<FileSystemDirectoryHandle> {
    return navigator.storage.getDirectory();
  }

  async readText(path: string): Promise<string | null> {
    const segments = splitPath(path);
    try {
      const root = await this.root();
      const { parent, fileName } = await resolveParentDirectory(
        root,
        segments,
        false,
      );
      const handle = await parent.getFileHandle(fileName);
      const file = await handle.getFile();
      return await file.text();
    } catch (error) {
      if (error instanceof DOMException && error.name === 'NotFoundError') {
        return null;
      }
      throw error;
    }
  }

  async writeText(path: string, content: string): Promise<void> {
    const segments = splitPath(path);
    const root = await this.root();
    const { parent, fileName } = await resolveParentDirectory(
      root,
      segments,
      true,
    );
    const handle = await parent.getFileHandle(fileName, { create: true });
    const writable = await handle.createWritable();
    try {
      await writable.write(content);
      await writable.close();
    } catch (error) {
      try {
        await writable.abort();
      } catch {
        // ignore abort failures
      }
      throw error;
    }
  }

  async remove(path: string): Promise<void> {
    const segments = splitPath(path);
    try {
      const root = await this.root();
      const { parent, fileName } = await resolveParentDirectory(
        root,
        segments,
        false,
      );
      await parent.removeEntry(fileName);
    } catch (error) {
      if (error instanceof DOMException && error.name === 'NotFoundError') {
        return;
      }
      throw error;
    }
  }

  async listConversationSummaries(userId: string) {
    if (!(await this.isAvailable())) {
      return [];
    }
    const items: Array<{
      path: string;
      updatedAt: string;
      lastSummarizedTurnNo: number | null;
    }> = [];
    try {
      const root = await this.root();
      let cursor: FileSystemDirectoryHandle = root;
      for (const segment of ['memory', 'v1', 'users', userId, 'conversations']) {
        cursor = await cursor.getDirectoryHandle(segment);
      }
      const dirHandle = cursor as FileSystemDirectoryHandle & {
        entries?: () => AsyncIterableIterator<[string, FileSystemHandle]>;
      };
      if (typeof dirHandle.entries !== 'function') {
        return [];
      }
      for await (const [conversationId, handle] of dirHandle.entries()) {
        if (handle.kind !== 'directory') {
          continue;
        }
        try {
          const dir = handle as FileSystemDirectoryHandle;
          const fileHandle = await dir.getFileHandle('对话摘要.md');
          const file = await fileHandle.getFile();
          items.push({
            path: `/memory/v1/users/${userId}/conversations/${conversationId}/对话摘要.md`,
            updatedAt: new Date(file.lastModified).toISOString(),
            lastSummarizedTurnNo: null,
          });
        } catch {
          // skip missing summary files
        }
      }
    } catch {
      return [];
    }
    return items;
  }
}
