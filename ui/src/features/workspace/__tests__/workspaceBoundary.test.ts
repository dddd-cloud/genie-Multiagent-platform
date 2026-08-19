import { describe, expect, it, vi } from 'vitest';
import { buildWorkspaceScope } from '@/platform/workspace/scope';
import { MemoryWorkspaceFileStore } from '@/platform/workspace/MemoryWorkspaceFileStore';
import {
  WorkspaceError,
  createWorkspaceRecord,
  type WorkspaceRemoteFile,
} from '@/platform/workspace/types';
import { WorkspaceService } from '@/services/workspace/workspaceService';
import type { WorkspaceRemoteAdapter } from '@/services/files/fileToolClient';

const scopeA = buildWorkspaceScope('user-a', 'workspace-a');
const scopeB = buildWorkspaceScope('user-b', 'workspace-b');

function bytes(value: string): ArrayBuffer {
  return new TextEncoder().encode(value).buffer;
}

describe('workspace file boundary', () => {
  it('never reads a file through another scope', async () => {
    const store = new MemoryWorkspaceFileStore();
    const record = createWorkspaceRecord(scopeA, {
      id: 'file-a',
      name: 'notes.txt',
      mimeType: 'text/plain',
      bytes: bytes('private'),
    });
    await store.put(scopeA, record);

    expect(await store.read(scopeB, 'file-a')).toBeNull();
    expect(await store.list(scopeB)).toEqual([]);
    expect(await store.read(scopeA, 'file-a')).toEqual(bytes('private'));
  });

  it('rejects path-like names and duplicate names', async () => {
    expect(() =>
      createWorkspaceRecord(scopeA, {
        name: '../secret.txt',
        bytes: bytes('x'),
      }),
    ).toThrowError(WorkspaceError);

    const store = new MemoryWorkspaceFileStore();
    await store.put(
      scopeA,
      createWorkspaceRecord(scopeA, { name: 'same.txt', bytes: bytes('a') }),
    );
    await expect(
      store.put(
        scopeA,
        createWorkspaceRecord(scopeA, { name: 'same.txt', bytes: bytes('b') }),
      ),
    ).rejects.toMatchObject({ code: 'DUPLICATE_FILE_NAME' });
  });

  it('keeps a local file when remote synchronization fails', async () => {
    const store = new MemoryWorkspaceFileStore();
    const remote: WorkspaceRemoteAdapter = {
      list: vi.fn(async () => []),
      upload: vi.fn(async () => {
        throw new Error('remote offline');
      }),
      download: vi.fn(async () => bytes('remote')),
    };
    const service = new WorkspaceService(store, remote);

    const result = await service.upload(scopeA, {
      name: 'script.py',
      mimeType: 'text/x-python',
      bytes: bytes('print(1)'),
    });

    expect(result.file.syncStatus).toBe('sync-failed');
    expect(result.syncError).toBe('remote offline');
    expect(await store.list(scopeA)).toHaveLength(1);
  });

  it('imports remote content only into the active scope', async () => {
    const store = new MemoryWorkspaceFileStore();
    const remoteFile: WorkspaceRemoteFile = {
      requestId: 'workspace-v1-scope-a',
      fileName: 'generated.csv',
      fileSize: 7,
    };
    const remote: WorkspaceRemoteAdapter = {
      list: vi.fn(async () => [remoteFile]),
      upload: vi.fn(async () => remoteFile),
      download: vi.fn(async () => bytes('a,b\n1,2')),
    };
    const service = new WorkspaceService(store, remote);

    const imported = await service.importRemote(scopeA, remoteFile);

    expect(imported.file.source).toBe('imported');
    expect(await store.read(scopeA, imported.file.id)).toEqual(bytes('a,b\n1,2'));
    expect(await store.read(scopeB, imported.file.id)).toBeNull();
  });

  it('rejects malformed binary records before they enter a scope', async () => {
    const store = new MemoryWorkspaceFileStore();
    const record = createWorkspaceRecord(scopeA, {
      id: 'binary-record',
      name: 'report.txt',
      bytes: bytes('safe'),
    });
    const malformed = {
      ...record,
      bytes: new Uint8Array([1, 2, 3]) as unknown as ArrayBuffer,
    };

    await expect(store.put(scopeA, malformed)).rejects.toMatchObject({ code: 'INVALID_FILE' });
    expect(await store.list(scopeA)).toEqual([]);
  });

  it('does not mark a file as synced when remote metadata is incomplete', async () => {
    const store = new MemoryWorkspaceFileStore();
    const remote: WorkspaceRemoteAdapter = {
      list: vi.fn(async () => []),
      upload: vi.fn(async () => ({
        requestId: 'workspace-v1-foreign',
        fileName: 'report.csv',
      })),
      download: vi.fn(async () => bytes('remote')),
    };
    const service = new WorkspaceService(store, remote);

    const result = await service.upload(scopeA, {
      name: 'report.csv',
      bytes: bytes('test'),
    });

    expect(result.file.syncStatus).toBe('sync-failed');
    expect(result.file.remote).toBeUndefined();
  });
});
