import { memo } from 'react';
import { Tooltip } from 'antd';
import type { UsageTotals } from '@/contracts';
import {
  formatAverageDuration,
  formatCount,
  formatTokens,
  successRate,
  TOKENS_UNAVAILABLE,
} from './usageFormat';

type UsageTotalsCardsProps = {
  totals: UsageTotals;
};

const UsageTotalsCards = memo(({ totals }: UsageTotalsCardsProps) => {
  const cards: Array<{ label: string; value: string; hint?: string }> = [
    {
      label: '调用次数',
      value: formatCount(totals.calls),
    },
    {
      label: '成功率',
      value: successRate(totals),
      hint: `成功 ${formatCount(totals.completedCalls)} · 失败 ${formatCount(
        totals.failedCalls,
      )} · 中断 ${formatCount(totals.interruptedCalls)}`,
    },
    {
      label: '平均耗时',
      value: formatAverageDuration(totals),
    },
    {
      label: 'Token 用量',
      value: formatTokens(totals),
      hint: totals.tokensAvailable
        ? `输入 ${formatCount(totals.promptTokens)} · 输出 ${formatCount(
          totals.completionTokens,
        )}`
        : '当前流式链路还没有回传 token 计数，所以这里不显示数字，而不是显示 0。',
    },
  ];

  return (
    <div
      className="grid grid-cols-2 gap-12 md:grid-cols-4"
      data-testid="usage-totals"
    >
      {cards.map((card) => (
        <div key={card.label} className="rounded-xl bg-surface px-16 py-14 shadow-xs">
          <div className="text-[13px] text-text-tertiary">{card.label}</div>
          {card.hint ? (
            <Tooltip title={card.hint}>
              <div className="mt-4 cursor-help text-[20px] font-semibold text-text-primary">
                {card.value}
              </div>
            </Tooltip>
          ) : (
            <div className="mt-4 text-[20px] font-semibold text-text-primary">
              {card.value}
            </div>
          )}
          {card.value === TOKENS_UNAVAILABLE ? (
            <div className="mt-2 text-[12px] leading-[18px] text-text-tertiary">
              等链路回传后自动生效
            </div>
          ) : null}
        </div>
      ))}
    </div>
  );
});

UsageTotalsCards.displayName = 'UsageTotalsCards';

export default UsageTotalsCards;
