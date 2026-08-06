import { describe, expect, it } from 'vitest';
import {
  validateMemoryPatchItem,
  validateMemoryPatches,
} from '../memoryPatchValidator';

describe('MemoryPatchValidatorTest', () => {
  it('accepts valid UPSERT and DELETE patches', () => {
    const result = validateMemoryPatches([
      {
        operation: 'UPSERT',
        section: '基本信息',
        key: 'preferredName',
        value: 'Alex',
      },
      {
        operation: 'DELETE',
        section: '回答偏好',
        key: 'language',
        value: null,
      },
    ]);
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.patches).toHaveLength(2);
    }
  });

  it('rejects secrets in key or value', () => {
    expect(
      validateMemoryPatchItem({
        operation: 'UPSERT',
        section: '基本信息',
        key: 'api_token',
        value: 'x',
      }).ok,
    ).toBe(false);

    expect(
      validateMemoryPatchItem({
        operation: 'UPSERT',
        section: '基本信息',
        key: 'note',
        value: 'my password is 123',
      }).ok,
    ).toBe(false);

    expect(
      validateMemoryPatchItem({
        operation: 'UPSERT',
        section: '基本信息',
        key: 'note',
        value: 'sk-abcdefghijklmnop',
      }).ok,
    ).toBe(false);

    expect(
      validateMemoryPatchItem({
        operation: 'UPSERT',
        section: '基本信息',
        key: 'note',
        value: 'Authorization: Bearer abc',
      }).ok,
    ).toBe(false);
  });

  it('rejects bad keys and overlong values', () => {
    expect(
      validateMemoryPatchItem({
        operation: 'UPSERT',
        section: '基本信息',
        key: 'bad#key',
        value: 'ok',
      }).ok,
    ).toBe(false);

    expect(
      validateMemoryPatchItem({
        operation: 'UPSERT',
        section: '基本信息',
        key: 'has\nnewline',
        value: 'ok',
      }).ok,
    ).toBe(false);

    expect(
      validateMemoryPatchItem({
        operation: 'UPSERT',
        section: '基本信息',
        key: 'ok',
        value: 'x'.repeat(501),
      }).ok,
    ).toBe(false);
  });

  it('rejects invalid section or operation', () => {
    expect(
      validateMemoryPatchItem({
        operation: 'MERGE',
        section: '基本信息',
        key: 'a',
        value: 'b',
      }).ok,
    ).toBe(false);

    expect(
      validateMemoryPatchItem({
        operation: 'UPSERT',
        section: '临时笔记',
        key: 'a',
        value: 'b',
      }).ok,
    ).toBe(false);
  });
});
