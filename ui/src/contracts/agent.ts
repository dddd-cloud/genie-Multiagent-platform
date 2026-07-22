import type { JsonObject } from './snapshot';

export const OUTPUT_STYLES =
  ['dataAgent', 'html', 'docs', 'ppt', 'table'] as const;

export type OutputStyle = (typeof OUTPUT_STYLES)[number];

export interface QueryAgentStreamRequest {
  sessionId: string;
  requestId: string;
  query: string;
  deepThink?: 0 | 1;
  outputStyle?: OutputStyle;
}

export interface GptProcessResultEvent {
  status: string | null;
  response: string;
  responseAll: string;
  finished: boolean;
  useTimes: number;
  useTokens: number;
  resultMap: JsonObject | null;
  responseType: string;
  traceId: string | null;
  reqId: string | null;
  encrypted: boolean;
  query: string | null;
  messages: string[] | null;
  packageType: string;
  errorMsg: string | null;
}
