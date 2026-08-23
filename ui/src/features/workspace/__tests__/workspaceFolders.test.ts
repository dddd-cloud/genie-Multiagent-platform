import { describe, expect, it } from 'vitest';
import { buildWorkspaceScope } from '@/platform/workspace/scope';
import { MemoryWorkspaceFileStore } from '@/platform/workspace/MemoryWorkspaceFileStore';
import { WorkspaceError, createWorkspaceRecord } from '@/platform/workspace/types';

const scope = buildWorkspaceScope('user-a', 'workspace-a');

function bytes(value: string): ArrayBuffer {
  return new TextEncoder().encode(value).buffer;
}

describe('workspace folders', () => {
  it('creates nested folders and lists them by scope', async () => {
    const store = new MemoryWorkspaceFileStore();
    const root = await store.createFolder(scope, '文档', null);
    const child = await store.createFolder(scope, '草稿', root.id);
    expect(child.parentId).toBe(root.id);
    const all = await store.listFolders(scope);
    expect(all.map((folder) => folder.name).sort()).toEqual(['文档', '草稿']);
  });

  it('rejects a duplicate name among siblings, file or folder', async () => {
    const store = new MemoryWorkspaceFileStore();
    await store.createFolder(scope, 'notes', null);
    await expect(store.createFolder(scope, 'notes', null)).rejects.toBeInstanceOf(WorkspaceError);

    await store.put(scope, createWorkspaceRecord(scope, { name: 'a.txt', bytes: bytes('a') }));
    await expect(store.createFolder(scope, 'a.txt', null)).rejects.toBeInstanceOf(WorkspaceError);
  });

  it('allows the same name in different folders', async () => {
    const store = new MemoryWorkspaceFileStore();
    const folderA = await store.createFolder(scope, 'A', null);
    const folderB = await store.createFolder(scope, 'B', null);
    await store.put(scope, createWorkspaceRecord(scope, { name: 'same.txt', bytes: bytes('1'), parentId: folderA.id }));
    const moved = await store.put(scope, createWorkspaceRecord(scope, { name: 'same.txt', bytes: bytes('2'), parentId: folderB.id }));
    expect(moved.parentId).toBe(folderB.id);
  });

  it('cascades folder deletion to descendant folders and files', async () => {
    const store = new MemoryWorkspaceFileStore();
    const parent = await store.createFolder(scope, 'parent', null);
    const child = await store.createFolder(scope, 'child', parent.id);
    const file = await store.put(
      scope,
      createWorkspaceRecord(scope, { name: 'leaf.txt', bytes: bytes('x'), parentId: child.id }),
    );

    await store.deleteFolder(scope, parent.id);

    expect(await store.listFolders(scope)).toEqual([]);
    expect(await store.read(scope, file.id)).toBeNull();
  });

  it('rejects moving a folder into its own descendant', async () => {
    const store = new MemoryWorkspaceFileStore();
    const parent = await store.createFolder(scope, 'parent', null);
    const child = await store.createFolder(scope, 'child', parent.id);
    await expect(store.moveFolder(scope, parent.id, child.id)).rejects.toBeInstanceOf(WorkspaceError);
    await expect(store.moveFolder(scope, parent.id, parent.id)).rejects.toBeInstanceOf(WorkspaceError);
  });

  it('moves a file between folders', async () => {
    const store = new MemoryWorkspaceFileStore();
    const folder = await store.createFolder(scope, 'target', null);
    const file = await store.put(scope, createWorkspaceRecord(scope, { name: 'f.txt', bytes: bytes('x') }));
    expect(file.parentId).toBeNull();
    const moved = await store.moveFile(scope, file.id, folder.id);
    expect(moved.parentId).toBe(folder.id);
  });

  it('renames a folder while checking sibling collisions', async () => {
    const store = new MemoryWorkspaceFileStore();
    await store.createFolder(scope, 'existing', null);
    const target = await store.createFolder(scope, 'renamable', null);
    await expect(store.renameFolder(scope, target.id, 'existing')).rejects.toBeInstanceOf(WorkspaceError);
    const renamed = await store.renameFolder(scope, target.id, 'renamed');
    expect(renamed.name).toBe('renamed');
  });
});
