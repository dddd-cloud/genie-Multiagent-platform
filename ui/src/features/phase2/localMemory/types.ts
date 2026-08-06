export type OpfsStatus = 'READY' | 'UNAVAILABLE' | 'CORRUPTED' | 'ERROR' | 'EMPTY';

export type MemoryTaskType = 'ANALYZE_TURN' | 'SUMMARIZE_CONVERSATION';

export type MemoryTaskStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'RETRY'
  | 'DONE'
  | 'FAILED';

export interface MemoryIndexRecord {
  userId: string;
  path: string;
  schemaVersion: 1;
  updatedAt: string;
  lastSummarizedTurnNo: number | null;
}

export interface MemoryTaskRecord {
  userId: string;
  conversationId: string;
  requestId: string;
  type: MemoryTaskType;
  status: MemoryTaskStatus;
  retryAt: number;
  attempt: number;
}

export interface LongTermMemoryEntry {
  key: string;
  value: string;
}

export interface LongTermMemoryDoc {
  schemaVersion: 1;
  updatedAt: string;
  sections: Record<
    '基本信息' | '回答偏好' | '长期目标' | '长期约束',
    LongTermMemoryEntry[]
  >;
}

export interface ConversationSummaryDoc {
  schemaVersion: 1;
  conversationId: string;
  lastSummarizedTurnNo: number;
  updatedAt: string;
  sections: Record<
    '当前目标' | '已确认事实' | '已完成内容' | '未解决事项',
    string
  >;
}

export const LONG_TERM_SECTION_NAMES = [
  '基本信息',
  '回答偏好',
  '长期目标',
  '长期约束',
] as const;

export type LongTermSectionName = (typeof LONG_TERM_SECTION_NAMES)[number];

export const SUMMARY_SECTION_NAMES = [
  '当前目标',
  '已确认事实',
  '已完成内容',
  '未解决事项',
] as const;

export type SummarySectionName = (typeof SUMMARY_SECTION_NAMES)[number];

export const MEMORY_LIMITS = {
  KEY_MAX_CODEPOINTS: 64,
  VALUE_MAX_CODEPOINTS: 500,
  LTM_MAX_CODEPOINTS: 12_000,
  SUMMARY_MAX_CODEPOINTS: 20_000,
  SUMMARY_SECTION_MAX_CODEPOINTS: 5_000,
  LOCAL_CONTEXT_WARN_CODEPOINTS: 27_000,
  LOCAL_CONTEXT_MAX_CODEPOINTS: 30_000,
  SUMMARIZE_TURN_DELTA: 5,
  MAX_ATTEMPT: 3,
} as const;

export const MEMORY_BACKOFF_MS = [5_000, 10_000, 20_000] as const;

export const MEMORY_LEASE_MS = 60_000;

export type MemoryErrorCode =
  | 'OPFS_UNAVAILABLE'
  | 'OPFS_WRITE_MISMATCH'
  | 'OPFS_WRITE_FAILED'
  | 'OPFS_CORRUPTED'
  | 'MEMORY_VALIDATION_FAILED'
  | 'MEMORY_SECRET_REJECTED'
  | 'MEMORY_ACCOUNT_MISMATCH'
  | 'MEMORY_TASK_DISCARDED'
  | 'MEMORY_ANALYSIS_FAILED'
  | 'SUMMARY_FAILED'
  | 'MEMORY_RETRYABLE'
  | 'MEMORY_FATAL';

export class MemoryError extends Error {
  readonly retryable: boolean;
  readonly errorCode: MemoryErrorCode;

  constructor(
    errorCode: MemoryErrorCode,
    message: string,
    retryable: boolean,
  ) {
    super(message);
    this.name = 'MemoryError';
    this.errorCode = errorCode;
    this.retryable = retryable;
  }
}

export function codePointLength(text: string): number {
  return [...text].length;
}
