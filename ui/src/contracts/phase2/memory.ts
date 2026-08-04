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

type MemoryPatchSection =
  | '基本信息'
  | '回答偏好'
  | '长期目标'
  | '长期约束';

export type MemoryPatchItem =
  | {
      operation: 'UPSERT';
      section: MemoryPatchSection;
      key: string;
      value: string;
    }
  | {
      operation: 'DELETE';
      section: MemoryPatchSection;
      key: string;
      value: null;
    };

export interface MemoryPatchResponse {
  schemaVersion: 1;
  patches: MemoryPatchItem[];
}

export interface ConversationSummaryResponse {
  schemaVersion: 1;
  markdown: string;
}
