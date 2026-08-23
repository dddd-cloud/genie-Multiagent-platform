import { afterEach, describe, expect, it, vi } from 'vitest';
import { buildWorkspaceScope } from '@/platform/workspace/scope';
import { MemoryWorkspaceFileStore } from '@/platform/workspace/MemoryWorkspaceFileStore';
import {
  WorkspaceError,
  createWorkspaceRecord,
  type WorkspaceRemoteFile,
} from '@/platform/workspace/types';
import { WorkspaceService } from '@/services/workspace/workspaceService';
import type { WorkspaceRemoteAdapter } from '@/services/files/fileToolClient';
import {
  bindWorkspaceExecutionContext,
  buildBoundWorkspaceChatContext,
  saveGeneratedFilesToWorkspace,
} from '@/features/workspace/executionBind';
import { createWorkspaceExecutionFileBridge } from '@/services/workspace/workspaceExecutionFiles';
import type { BrowserSkillExecutionSignal } from '@/contracts';

const scopeA = buildWorkspaceScope('user-a', 'workspace-a');
const scopeB = buildWorkspaceScope('user-b', 'workspace-b');

function bytes(value: string): ArrayBuffer {
  return new TextEncoder().encode(value).buffer;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('workspace file boundary', () => {
  it('keeps the same workspace scope across conversations', () => {
    const first = buildWorkspaceScope('user-a', 'workspace-a', 'conversation-1');
    const second = buildWorkspaceScope('user-a', 'workspace-a', 'conversation-2');
    expect(first.key).toBe(second.key);
    expect(first.conversationId).not.toBe(second.conversationId);
  });

  it('exposes the selected browser workspace as bounded untrusted chat context', async () => {
    const store = new MemoryWorkspaceFileStore();
    const service = new WorkspaceService(store, null);
    const file = await service.write(scopeA, {
      name: 'script.py',
      mimeType: 'text/x-python',
      bytes: bytes('print("browser workspace")'),
    });
    bindWorkspaceExecutionContext({ service, scope: scopeA, fileIds: [file.id] });

    const context = await buildBoundWorkspaceChatContext();

    expect(context).toContain('[UNTRUSTED_BROWSER_WORKSPACE]');
    expect(context).toContain('/workspace/script.py');
    expect(context).toContain('Python 代码');
    expect(context).toContain('轻量索引');
    expect(context).not.toContain('print("browser workspace")');
    expect(context).toContain('[/UNTRUSTED_BROWSER_WORKSPACE]');
    bindWorkspaceExecutionContext(null);
  });

  it('copies generated conversation files into the selected fixed workspace', async () => {
    const store = new MemoryWorkspaceFileStore();
    const service = new WorkspaceService(store, null);
    const refresh = vi.fn(async () => undefined);
    const firstBytes = bytes('a,b\n1,2');
    const secondBytes = bytes('a,b\n3,4');
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(firstBytes, {
        status: 200,
        headers: { 'content-type': 'text/csv', 'content-length': String(firstBytes.byteLength) },
      }))
      .mockResolvedValueOnce(new Response(secondBytes, {
        status: 200,
        headers: { 'content-type': 'text/csv', 'content-length': String(secondBytes.byteLength) },
      }));
    vi.stubGlobal('fetch', fetchMock);
    const execution = { service, scope: scopeA, fileIds: [], refresh };

    const first = await saveGeneratedFilesToWorkspace(execution, [{
      name: 'generated.csv',
      url: '/v1/file_tool/preview/run-1/generated.csv',
      type: 'csv',
      size: firstBytes.byteLength,
    }]);
    const original = (await service.list(scopeA))[0];
    const second = await saveGeneratedFilesToWorkspace(execution, [{
      name: 'generated.csv',
      url: '/v1/file_tool/download/run-1/generated.csv',
      type: 'csv',
      size: secondBytes.byteLength,
    }]);
    const files = await service.list(scopeA);

    expect(first).toEqual({ saved: ['generated.csv'], failures: [] });
    expect(second).toEqual({ saved: ['generated.csv'], failures: [] });
    expect(files).toHaveLength(1);
    expect(files[0].id).toBe(original.id);
    expect(files[0].source).toBe('assistant');
    expect(await service.read(scopeA, files[0].id)).toEqual(secondBytes);
    expect(fetchMock).toHaveBeenNthCalledWith(1, '/v1/file_tool/download/run-1/generated.csv', expect.objectContaining({
      credentials: 'include',
    }));
    expect(refresh).toHaveBeenCalledTimes(2);
  });

  it('rejects foreign generated-file urls without issuing a request', async () => {
    const store = new MemoryWorkspaceFileStore();
    const service = new WorkspaceService(store, null);
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const result = await saveGeneratedFilesToWorkspace(
      { service, scope: scopeA, fileIds: [] },
      [{ name: 'foreign.csv', url: 'https://example.com/foreign.csv', size: 4 }],
    );

    expect(result.saved).toEqual([]);
    expect(result.failures).toHaveLength(1);
    expect(fetchMock).not.toHaveBeenCalled();
    expect(await service.list(scopeA)).toEqual([]);
  });

  it('lets browser Python outputs update files in the selected workspace', async () => {
    const store = new MemoryWorkspaceFileStore();
    const service = new WorkspaceService(store, null);
    const original = await service.write(scopeA, {
      name: 'result.txt',
      mimeType: 'text/plain',
      bytes: bytes('old'),
    });
    const bridge = createWorkspaceExecutionFileBridge({
      service,
      scope: scopeA,
      fileIds: [original.id],
    });
    const signal = {} as BrowserSkillExecutionSignal;

    await bridge.saveOutputFiles(signal, [{
      name: 'result.txt',
      mimeType: 'text/plain',
      bytes: bytes('new'),
    }]);

    const files = await service.list(scopeA);
    expect(files).toHaveLength(1);
    expect(files[0].id).toBe(original.id);
    expect(await service.read(scopeA, original.id)).toEqual(bytes('new'));
  });

  it('lets browser Python delete only explicitly mounted workspace files', async () => {
    const store = new MemoryWorkspaceFileStore();
    const service = new WorkspaceService(store, null);
    const granted = await service.write(scopeA, {
      name: 'generated.csv',
      mimeType: 'text/csv',
      bytes: bytes('a,b\n1,2'),
    });
    await service.write(scopeA, {
      name: 'private.txt',
      mimeType: 'text/plain',
      bytes: bytes('not mounted'),
    });
    const bridge = createWorkspaceExecutionFileBridge({
      service,
      scope: scopeA,
      fileIds: [granted.id],
    });
    const signal = {} as BrowserSkillExecutionSignal;

    await expect(
      bridge.deleteFilesByName(signal, ['generated.csv']),
    ).resolves.toEqual(['generated.csv']);
    expect((await service.list(scopeA)).map((file) => file.name)).toEqual(['private.txt']);
    await expect(
      bridge.deleteFilesByName(signal, ['private.txt']),
    ).rejects.toMatchObject({ code: 'SCOPE_MISMATCH' });
  });

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
