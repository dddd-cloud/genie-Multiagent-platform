import { describe, expect, it } from 'vitest';
import { FakeMemoryIndexStore } from '../FakeMemoryIndexStore';
import { FakePrivateFileSystem } from '../FakePrivateFileSystem';
import { emptyLongTermMemoryDoc } from '../markdownSerializer';
import { MemoryRepository } from '../memoryRepository';
import { buildLongTermMemoryPath } from '../paths';
import { MemoryError } from '../types';

describe('OpfsWriteVerificationTest', () => {
  it('updates index only after exact read-back success', async () => {
    const fs = new FakePrivateFileSystem();
    const store = new FakeMemoryIndexStore();
    const repository = new MemoryRepository('user-a', fs, store);
    const doc = emptyLongTermMemoryDoc('2026-08-06T00:00:00.000Z');
    doc.sections.基本信息.push({
      key: 'preferredName',
      value: 'Alex'
    });

    await repository.writeLongTermMemory(doc);

    const path = buildLongTermMemoryPath('user-a');
    const index = await store.getIndex('user-a', path);
    expect(index).not.toBeNull();
    expect(index?.updatedAt).toBe(doc.updatedAt);
    const raw = await fs.readText(path);
    expect(raw).toContain('preferredName');
  });

  it('does not update index on read-back mismatch', async () => {
    const fs = new FakePrivateFileSystem({ readBackMismatch: true });
    const store = new FakeMemoryIndexStore();
    const repository = new MemoryRepository('user-a', fs, store);
    const doc = emptyLongTermMemoryDoc('2026-08-06T00:00:00.000Z');
    doc.sections.基本信息.push({
      key: 'preferredName',
      value: 'Alex'
    });

    await expect(repository.writeLongTermMemory(doc)).rejects.toMatchObject({
      errorCode: 'OPFS_WRITE_MISMATCH',
      retryable: true,
    });
    expect(await store.listIndex('user-a')).toHaveLength(0);
  });

  it('does not update index on write failure', async () => {
    const fs = new FakePrivateFileSystem({ writeError: 'disk full' });
    const store = new FakeMemoryIndexStore();
    const repository = new MemoryRepository('user-a', fs, store);
    const doc = emptyLongTermMemoryDoc('2026-08-06T00:00:00.000Z');

    await expect(repository.writeLongTermMemory(doc)).rejects.toBeInstanceOf(
      MemoryError,
    );
    expect(await store.listIndex('user-a')).toHaveLength(0);
  });

  it('preserves corrupted raw content on read', async () => {
    const fs = new FakePrivateFileSystem();
    const store = new FakeMemoryIndexStore();
    const repository = new MemoryRepository('user-a', fs, store);
    const path = buildLongTermMemoryPath('user-a');
    const corrupted = 'not-a-valid-memory-file';
    fs.seed(path, corrupted);

    const result = await repository.readLongTermMemory();
    expect(result.status).toBe('CORRUPTED');
    if (result.status === 'CORRUPTED') {
      expect(result.raw).toBe(corrupted);
    }
    expect(await fs.readText(path)).toBe(corrupted);
  });
});
