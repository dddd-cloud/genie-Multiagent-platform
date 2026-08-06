import {
  LONG_TERM_SECTION_NAMES,
  SUMMARY_SECTION_NAMES,
  type ConversationSummaryDoc,
  type LongTermMemoryDoc,
} from './types';

function compareKey(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0;
}

export function serializeLongTermMemory(doc: LongTermMemoryDoc): string {
  const lines: string[] = [
    '---',
    `schemaVersion: ${doc.schemaVersion}`,
    `updatedAt: ${doc.updatedAt}`,
    '---',
    '',
  ];

  for (const section of LONG_TERM_SECTION_NAMES) {
    lines.push(`## ${section}`);
    const entries = [...doc.sections[section]].sort((a, b) =>
      compareKey(a.key, b.key),
    );
    for (const entry of entries) {
      lines.push(
        `- ${JSON.stringify({
          key: entry.key,
          value: entry.value
        })}`,
      );
    }
    lines.push('');
  }

  return `${lines.join('\n').replace(/\n+$/, '')}\n`;
}

export function serializeConversationSummary(
  doc: ConversationSummaryDoc,
): string {
  const lines: string[] = [
    '---',
    `schemaVersion: ${doc.schemaVersion}`,
    `conversationId: ${doc.conversationId}`,
    `lastSummarizedTurnNo: ${doc.lastSummarizedTurnNo}`,
    `updatedAt: ${doc.updatedAt}`,
    '---',
    '',
  ];

  for (const section of SUMMARY_SECTION_NAMES) {
    lines.push(`## ${section}`);
    const body = doc.sections[section] ?? '';
    if (body.length > 0) {
      lines.push(body);
    }
    lines.push('');
  }

  return `${lines.join('\n').replace(/\n+$/, '')}\n`;
}

export function emptyLongTermMemoryDoc(updatedAt = new Date().toISOString()): LongTermMemoryDoc {
  return {
    schemaVersion: 1,
    updatedAt,
    sections: {
      基本信息: [],
      回答偏好: [],
      长期目标: [],
      长期约束: [],
    },
  };
}

export function emptyConversationSummaryDoc(
  conversationId: string,
  lastSummarizedTurnNo = 0,
  updatedAt = new Date().toISOString(),
): ConversationSummaryDoc {
  return {
    schemaVersion: 1,
    conversationId,
    lastSummarizedTurnNo,
    updatedAt,
    sections: {
      当前目标: '',
      已确认事实: '',
      已完成内容: '',
      未解决事项: '',
    },
  };
}
