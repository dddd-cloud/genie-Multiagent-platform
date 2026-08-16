import { describe, expect, it } from 'vitest';
import { emptyConversationSummaryDoc } from '../markdownSerializer';
import {
  conversationDisplayTitle,
  formatHostMemoryPath,
  formatRelativeUpdate,
  LONG_TERM_SECTION_UI,
  summaryPreview,
} from '../memorySettingsUi';
import { conversationIdFromSummaryPath } from '../paths';

describe('MemorySettingsUi', () => {
  it('translates Docker container path to the host data folder', () => {
    expect(formatHostMemoryPath('/var/lib/joyagent/memory')).toBe(
      '项目目录 / data / memory',
    );
    expect(formatHostMemoryPath('C:\\Users\\me\\.joyagent\\memory')).toBe(
      'C:\\Users\\me\\.joyagent\\memory',
    );
    expect(formatHostMemoryPath(null)).toBe('尚未创建数据目录');
  });

  it('formats relative update times in Chinese', () => {
    const now = new Date(2026, 7, 16, 20, 0, 0);
    expect(formatRelativeUpdate(new Date(2026, 7, 16, 1, 0, 0).toISOString(), now)).toBe(
      '今天更新',
    );
    expect(formatRelativeUpdate(new Date(2026, 7, 15, 23, 0, 0).toISOString(), now)).toBe(
      '昨天更新',
    );
    expect(formatRelativeUpdate(new Date(2026, 7, 13, 12, 0, 0).toISOString(), now)).toBe(
      '3天前更新',
    );
    expect(formatRelativeUpdate(new Date(2026, 6, 1, 0, 0, 0).toISOString(), now)).toBe(
      '2026年7月1日更新',
    );
  });

  it('falls back to unnamed conversation and extracts id from summary path', () => {
    expect(conversationDisplayTitle(null)).toBe('未命名对话');
    expect(conversationDisplayTitle('  ')).toBe('未命名对话');
    expect(conversationDisplayTitle('超市销售')).toBe('超市销售');
    expect(
      conversationIdFromSummaryPath(
        '/memory/v1/users/u1/conversations/conv-9/对话摘要.md',
      ),
    ).toBe('conv-9');
  });

  it('builds a human preview from current goal and facts', () => {
    const doc = emptyConversationSummaryDoc('c1', 5, '2026-08-16T00:00:00.000Z');
    expect(summaryPreview(doc)).toBe('暂无摘要内容');
    doc.sections.当前目标 = '整理生日清单';
    doc.sections.已确认事实 = '李四，20岁';
    expect(summaryPreview(doc)).toBe('整理生日清单 · 李四，20岁');
  });

  it('uses a distinct add-memory example for each section', () => {
    expect(LONG_TERM_SECTION_UI.基本信息.valuePlaceholder).toContain('李四');
    expect(LONG_TERM_SECTION_UI.回答偏好.keyPlaceholder).toContain('语气');
    expect(LONG_TERM_SECTION_UI.回答偏好.valuePlaceholder).toContain('简洁、幽默');
    expect(LONG_TERM_SECTION_UI.长期目标.keyPlaceholder).toContain('学习');
    expect(LONG_TERM_SECTION_UI.长期目标.valuePlaceholder).toContain('英语口语');
    expect(LONG_TERM_SECTION_UI.长期约束.keyPlaceholder).toContain('不要');
    expect(LONG_TERM_SECTION_UI.长期约束.valuePlaceholder).toContain('薪资');
  });
});
