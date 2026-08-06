import { describe, expect, it } from 'vitest';
import {
  parseConversationSummary,
  parseLongTermMemory,
} from '../markdownParser';
import {
  emptyLongTermMemoryDoc,
  serializeConversationSummary,
  serializeLongTermMemory,
} from '../markdownSerializer';

describe('MemoryMarkdownParserTest', () => {
  it('parses valid long-term memory with JSON entries', () => {
    const raw = serializeLongTermMemory({
      ...emptyLongTermMemoryDoc('2026-08-06T00:00:00.000Z'),
      sections: {
        基本信息: [{
          key: 'preferredName',
          value: 'Alex'
        }],
        回答偏好: [{
          key: 'language',
          value: 'zh-CN'
        }],
        长期目标: [],
        长期约束: [],
      },
    });
    const result = parseLongTermMemory(raw);
    expect(result.ok).toBe(true);
    if (!result.ok) {
      return;
    }
    expect(result.doc.sections.基本信息[0]?.value).toBe('Alex');
    expect(result.doc.sections.回答偏好[0]?.key).toBe('language');
  });

  it('returns CORRUPTED for missing section or duplicate keys', () => {
    const missingSection = `---
schemaVersion: 1
updatedAt: 2026-08-06T00:00:00.000Z
---

## 基本信息
`;
    const missing = parseLongTermMemory(missingSection);
    expect(missing.ok).toBe(false);
    if (!missing.ok) {
      expect(missing.status).toBe('CORRUPTED');
    }

    const dup = `---
schemaVersion: 1
updatedAt: 2026-08-06T00:00:00.000Z
---

## 基本信息
- {"key":"a","value":"1"}
- {"key":"a","value":"2"}

## 回答偏好

## 长期目标

## 长期约束
`;
    const dupResult = parseLongTermMemory(dup);
    expect(dupResult.ok).toBe(false);
  });

  it('parses conversation summary sections as plain body text', () => {
    const raw = serializeConversationSummary({
      schemaVersion: 1,
      conversationId: 'c1',
      lastSummarizedTurnNo: 5,
      updatedAt: '2026-08-06T00:00:00.000Z',
      sections: {
        当前目标: '完成报告',
        已确认事实: '偏好中文',
        已完成内容: '提纲',
        未解决事项: '数据源',
      },
    });
    const result = parseConversationSummary(raw);
    expect(result.ok).toBe(true);
    if (!result.ok) {
      return;
    }
    expect(result.doc.conversationId).toBe('c1');
    expect(result.doc.lastSummarizedTurnNo).toBe(5);
    expect(result.doc.sections.当前目标).toBe('完成报告');
  });

  it('does not auto-repair corrupted summary', () => {
    const bad = `---
schemaVersion: 1
conversationId: c1
lastSummarizedTurnNo: x
updatedAt: 2026-08-06T00:00:00.000Z
---

## 当前目标
`;
    const result = parseConversationSummary(bad);
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.status).toBe('CORRUPTED');
    }
  });
});
