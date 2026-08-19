import { memo, useCallback, useEffect, useState } from 'react';
import { Alert, Button, Segmented, Spin } from 'antd';
import type { UsageSummaryResponse } from '@/contracts';
import { useAuth } from '@/features/auth/useAuth';
import UsageDailyTrend from '@/features/usage/UsageDailyTrend';
import UsageTotalsCards from '@/features/usage/UsageTotalsCards';
import { recentRange } from '@/features/usage/usageFormat';
import { MvpApiError } from '@/services/apiError';
import { fetchMyUsageSummary } from '@/services/settings';

const RANGE_OPTIONS = [
  {
    label: '近 7 天',
    value: 7,
  },
  {
    label: '近 30 天',
    value: 30,
  },
  {
    label: '近 90 天',
    value: 90,
  },
];

const ROLE_LABELS: Record<string, string> = {
  ADMIN: '管理员',
  USER: '普通用户',
};

const AccountPage: GenieType.FC = memo(() => {
  const { user } = useAuth();
  const [days, setDays] = useState(30);
  const [summary, setSummary] = useState<UsageSummaryResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    async (rangeDays: number, signal?: AbortSignal) => {
      setLoading(true);
      setError(null);
      try {
        const data = await fetchMyUsageSummary(recentRange(rangeDays), signal);
        if (signal?.aborted) {
          return;
        }
        setSummary(data);
      } catch (err: unknown) {
        if (signal?.aborted) {
          return;
        }
        setError(err instanceof MvpApiError ? err.message : '读取用量失败');
      } finally {
        if (!signal?.aborted) {
          setLoading(false);
        }
      }
    },
    [],
  );

  useEffect(() => {
    const controller = new AbortController();
    void load(days, controller.signal);
    return () => controller.abort();
  }, [days, load]);

  return (
    <div className="flex flex-col gap-28" data-testid="settings-account">
      <section>
        <h2 className="m-0 mb-8 px-4 text-[13px] font-medium tracking-[0.02em] text-text-tertiary">
          当前账户
        </h2>
        <div className="overflow-hidden rounded-xl bg-surface px-16 py-14 shadow-xs">
          <dl className="m-0 grid grid-cols-1 gap-12 sm:grid-cols-3">
            <div>
              <dt className="text-[13px] text-text-tertiary">用户名</dt>
              <dd className="m-0 mt-2 text-[15px] text-text-primary">
                {user?.username ?? '—'}
              </dd>
            </div>
            <div>
              <dt className="text-[13px] text-text-tertiary">显示名</dt>
              <dd className="m-0 mt-2 text-[15px] text-text-primary">
                {user?.displayName || '—'}
              </dd>
            </div>
            <div>
              <dt className="text-[13px] text-text-tertiary">角色</dt>
              <dd className="m-0 mt-2 text-[15px] text-text-primary">
                {user ? ROLE_LABELS[user.role] ?? user.role : '—'}
              </dd>
            </div>
          </dl>
        </div>
      </section>

      <section>
        <div className="mb-8 flex flex-wrap items-center justify-between gap-8 px-4">
          <h2 className="m-0 text-[13px] font-medium tracking-[0.02em] text-text-tertiary">
            我的用量
          </h2>
          <Segmented
            size="small"
            value={days}
            options={RANGE_OPTIONS}
            onChange={(value) => setDays(Number(value))}
          />
        </div>

        {error ? (
          <Alert
            className="mb-12"
            type="warning"
            showIcon
            message={error}
            action={
              <Button size="small" onClick={() => void load(days)}>
                重试
              </Button>
            }
          />
        ) : null}

        <Spin spinning={loading}>
          {summary ? (
            <div className="flex flex-col gap-12">
              <UsageTotalsCards totals={summary.totals} />
              <UsageDailyTrend daily={summary.daily} />
            </div>
          ) : (
            <div className="rounded-xl bg-surface px-16 py-20 text-[14px] text-text-secondary shadow-xs">
              暂无用量数据。
            </div>
          )}
        </Spin>
      </section>
    </div>
  );
});

AccountPage.displayName = 'AccountPage';

export default AccountPage;
