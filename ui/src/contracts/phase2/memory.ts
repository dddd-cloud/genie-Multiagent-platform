export const MEMORY_PATCH_OPERATIONS = [
  'UPSERT',
  'DELETE',
] as const;

export const LONG_TERM_MEMORY_SECTIONS = [
  '基本信息',
  '回答偏好',
  '长期目标',
  '长期约束',
] as const;

export interface MemoryPatchItem {
  operation: 'UPSERT' | 'DELETE';
  section:
    | '基本信息'
    | '回答偏好'
    | '长期目标'
    | '长期约束';
  key: string;
  value: string | null;
}

export interface MemoryPatchResponse {
  schemaVersion: 1;
  patches: MemoryPatchItem[];
}

export interface ConversationSummaryResponse {
  schemaVersion: 1;
  markdown: string;
}
