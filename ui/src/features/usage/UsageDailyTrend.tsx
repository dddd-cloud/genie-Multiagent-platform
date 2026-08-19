import { memo } from 'react';
import type { UsageDailyPoint } from '@/contracts';
import { formatCount } from './usageFormat';

type UsageDailyTrendProps = {
  daily: UsageDailyPoint[];
};

/**
 * Deliberately a bar list rather than a chart: the trend only needs relative magnitude, and this
 * avoids pulling a charting dependency into the bundle for one screen.
 */
const UsageDailyTrend = memo(({ daily }: UsageDailyTrendProps) => {
  if (daily.length === 0) {
    return (
      <div className="rounded-xl bg-surface px-16 py-20 text-[14px] text-text-secondary shadow-xs">
        这段时间还没有调用记录。
      </div>
    );
  }

  const peak = daily.reduce((max, point) => Math.max(max, point.calls), 0) || 1;

  return (
    <div
      className="flex flex-col gap-6 rounded-xl bg-surface px-16 py-14 shadow-xs"
      data-testid="usage-daily-trend"
    >
      {daily.map((point) => (
        <div key={point.day} className="flex items-center gap-12">
          <span className="w-[86px] shrink-0 text-[12px] text-text-tertiary">
            {point.day}
          </span>
          <span className="h-8 min-w-0 flex-1 overflow-hidden rounded-full bg-[#F0F0F2]">
            <span
              className="block h-full rounded-full bg-brand"
              style={{ width: `${Math.max(2, (point.calls / peak) * 100)}%` }}
            />
          </span>
          <span className="w-[92px] shrink-0 text-right text-[12px] text-text-secondary">
            {formatCount(point.calls)} 次
            {point.failedCalls > 0 ? ` · 失败 ${point.failedCalls}` : ''}
          </span>
        </div>
      ))}
    </div>
  );
});

UsageDailyTrend.displayName = 'UsageDailyTrend';

export default UsageDailyTrend;
