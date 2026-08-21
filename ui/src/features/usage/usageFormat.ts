import type { UsageTotals } from '@/contracts';

/** Shown wherever token metrics are requested but the streaming path does not report them yet. */
export const TOKENS_UNAVAILABLE = '暂不可用';

export function formatCount(value: number): string {
  return Number.isFinite(value) ? value.toLocaleString('zh-CN') : '—';
}

export function formatDuration(totalMs: number): string {
  if (!Number.isFinite(totalMs) || totalMs <= 0) {
    return '—';
  }
  const seconds = Math.round(totalMs / 1000);
  if (seconds < 60) {
    return `${seconds} 秒`;
  }
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return `${minutes} 分 ${seconds % 60} 秒`;
  }
  return `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分`;
}

export function formatAverageDuration(totals: UsageTotals): string {
  if (totals.calls <= 0 || totals.totalDurationMs <= 0) {
    return '—';
  }
  return formatDuration(Math.round(totals.totalDurationMs / totals.calls));
}

export function formatTokens(totals: UsageTotals): string {
  if (totals.tokensAvailable) {
    return formatCount(totals.totalTokens);
  }
  if (totals.calls <= 0) {
    return formatCount(0);
  }
  return TOKENS_UNAVAILABLE;
}

export function successRate(totals: UsageTotals): string {
  if (totals.calls <= 0) {
    return '—';
  }
  return `${Math.round((totals.completedCalls / totals.calls) * 100)}%`;
}

/** Last `days` calendar days inclusive of today, as the `YYYY-MM-DD` bounds the API expects. */
export function recentRange(days: number): { from: string; to: string } {
  const to = new Date();
  const from = new Date(to.getTime());
  from.setDate(from.getDate() - (Math.max(1, days) - 1));
  return {
    from: toIsoDate(from),
    to: toIsoDate(to),
  };
}

function toIsoDate(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
