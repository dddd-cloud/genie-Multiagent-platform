import { describe, expect, it } from 'vitest';
import { parseLongTermMemory } from '../markdownParser';
import {
  emptyConversationSummaryDoc,
  emptyLongTermMemoryDoc,
  serializeConversationSummary,
  serializeLongTermMemory,
} from '../markdownSerializer';

describe('MemoryMarkdownSerializerTest', () => {
  it('emits YAML front matter and four fixed LTM sections', () => {
    const doc = emptyLongTermMemoryDoc('2026-08-06T00:00:00.000Z');
    doc.sections.基本信息.push({
      key: 'preferredName',
      value: 'Alex'
    });
    doc.sections.回答偏好.push({
      key: 'language',
      value: 'zh-CN'
    });
    const text = serializeLongTermMemory(doc);
    expect(text.startsWith('---\n')).toBe(true);
    expect(text).toContain('schemaVersion: 1');
    expect(text).toContain('## 基本信息');
    expect(text).toContain('## 回答偏好');
    expect(text).toContain('## 长期目标');
    expect(text).toContain('## 长期约束');
    expect(text).toContain(
      '- {"key":"preferredName","value":"Alex"}',
    );
  });

  it('stable-sorts keys within a section', () => {
    const doc = emptyLongTermMemoryDoc('2026-08-06T00:00:00.000Z');
    doc.sections.基本信息 = [
      {
        key: 'zeta',
        value: '1'
      },
      {
        key: 'alpha',
        value: '2'
      },
    ];
    const text = serializeLongTermMemory(doc);
    const alphaAt = text.indexOf('"alpha"');
    const zetaAt = text.indexOf('"zeta"');
    expect(alphaAt).toBeGreaterThan(-1);
    expect(zetaAt).toBeGreaterThan(alphaAt);
  });

  it('round-trips long-term memory', () => {
    const doc = emptyLongTermMemoryDoc('2026-08-06T00:00:00.000Z');
    doc.sections.长期目标.push({
      key: 'goal',
      value: 'ship phase2'
    });
    const parsed = parseLongTermMemory(serializeLongTermMemory(doc));
    expect(parsed.ok).toBe(true);
    if (!parsed.ok) {
      return;
    }
    expect(parsed.doc.sections.长期目标).toEqual([
      {
        key: 'goal',
        value: 'ship phase2'
      },
    ]);
  });

  it('serializes conversation summary with fixed sections', () => {
    const doc = emptyConversationSummaryDoc('conv-1', 3, '2026-08-06T00:00:00.000Z');
    doc.sections.当前目标 = 'finish docs';
    const text = serializeConversationSummary(doc);
    expect(text).toContain('conversationId: conv-1');
    expect(text).toContain('lastSummarizedTurnNo: 3');
    expect(text).toContain('## 当前目标\nfinish docs');
    expect(text).toContain('## 未解决事项');
  });
});
