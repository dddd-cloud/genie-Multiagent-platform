/**
 * `tokensAvailable` is false when recorded calls exist but none reported token counts. Empty
 * ranges should still show 0 rather than "unavailable".
 */
export interface UsageTotals {
  calls: number;
  completedCalls: number;
  failedCalls: number;
  interruptedCalls: number;
  totalDurationMs: number;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  tokensAvailable: boolean;
}

export interface UsageDailyPoint {
  day: string;
  calls: number;
  completedCalls: number;
  failedCalls: number;
  totalTokens: number;
}

export interface UsageSummaryResponse {
  from: string;
  to: string;
  totals: UsageTotals;
  daily: UsageDailyPoint[];
}

export interface UsageUserRow {
  userId: string;
  username: string | null;
  displayName: string | null;
  calls: number;
  completedCalls: number;
  failedCalls: number;
  totalDurationMs: number;
  totalTokens: number;
}

/** Inclusive `YYYY-MM-DD` bounds; both are optional and default to the last 30 days server-side. */
export interface UsageRangeQuery {
  from?: string;
  to?: string;
}
