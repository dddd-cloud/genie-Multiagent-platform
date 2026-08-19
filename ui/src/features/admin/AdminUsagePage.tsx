import { memo, useCallback, useEffect, useState } from 'react';
import { Alert, Button, Segmented, Spin, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { UsageSummaryResponse, UsageUserRow } from '@/contracts';
import UsageDailyTrend from '@/features/usage/UsageDailyTrend';
import UsageTotalsCards from '@/features/usage/UsageTotalsCards';
import {
  formatCount,
  formatDuration,
  recentRange,
  TOKENS_UNAVAILABLE,
} from '@/features/usage/usageFormat';
import { MvpApiError } from '@/services/apiError';
import { fetchAdminUsageSummary, listAdminUsageUsers } from '@/services/admin';

const PAGE_SIZE = 20;

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

const AdminUsagePage: GenieType.FC = memo(() => {
  const [days, setDays] = useState(30);
  const [summary, setSummary] = useState<UsageSummaryResponse | null>(null);
  const [rows, setRows] = useState<UsageUserRow[]>([]);
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    async (rangeDays: number, nextPage: number, signal?: AbortSignal) => {
      setLoading(true);
      setError(null);
      const range = recentRange(rangeDays);
      try {
        const [summaryData, usersData] = await Promise.all([
          fetchAdminUsageSummary(range, signal),
          listAdminUsageUsers(
            {
              ...range,
              page: nextPage,
              pageSize: PAGE_SIZE,
            },
            signal,
          ),
        ]);
        if (signal?.aborted) {
          return;
        }
        setSummary(summaryData);
        setRows(usersData?.items ?? []);
        setPage(usersData?.page ?? nextPage);
        setHasMore(usersData?.hasMore ?? false);
      } catch (err: unknown) {
        if (signal?.aborted) {
          return;
        }
        setError(
          err instanceof MvpApiError && err.code === 'ACCESS_DENIED'
            ? '当前账户没有管理员权限'
            : err instanceof MvpApiError
              ? err.message
              : '读取用量失败',
        );
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
    void load(days, 1, controller.signal);
    return () => controller.abort();
  }, [days, load]);

  const tokensAvailable = summary?.totals.tokensAvailable ?? false;

  const columns: ColumnsType<UsageUserRow> = [
    {
      title: '用户',
      key: 'user',
      render: (_: unknown, record: UsageUserRow) => (
        <div className="min-w-0">
          <div className="truncate text-[14px] text-text-primary">
            {record.displayName || record.username || record.userId}
          </div>
          <div className="truncate text-[12px] text-text-tertiary">
            {record.username ?? '账户已删除'}
          </div>
        </div>
      ),
    },
    {
      title: '调用次数',
      dataIndex: 'calls',
      key: 'calls',
      align: 'right',
      render: (value: number) => formatCount(value),
    },
    {
      title: '成功',
      dataIndex: 'completedCalls',
      key: 'completedCalls',
      align: 'right',
      render: (value: number) => formatCount(value),
    },
    {
      title: '失败',
      dataIndex: 'failedCalls',
      key: 'failedCalls',
      align: 'right',
      render: (value: number) => formatCount(value),
    },
    {
      title: '总耗时',
      dataIndex: 'totalDurationMs',
      key: 'totalDurationMs',
      align: 'right',
      render: (value: number) => formatDuration(value),
    },
    {
      title: 'Token',
      dataIndex: 'totalTokens',
      key: 'totalTokens',
      align: 'right',
      render: (value: number) =>
        tokensAvailable ? formatCount(value) : TOKENS_UNAVAILABLE,
    },
  ];

  return (
    <div className="h-full overflow-auto bg-page">
      <div className="mx-auto flex max-w-[1080px] flex-col gap-20 px-24 py-36">
        <header className="flex flex-wrap items-start justify-between gap-12">
          <div>
            <h1 className="m-0 text-[28px] font-semibold tracking-[-0.02em] text-text-primary">
              用量
            </h1>
            <p className="mt-8 mb-0 text-[15px] leading-[22px] text-text-secondary">
              按天统计本租户的模型调用情况
              {summary ? `（${summary.from} 至 ${summary.to}）` : ''}。
            </p>
          </div>
          <Segmented
            value={days}
            options={RANGE_OPTIONS}
            onChange={(value) => setDays(Number(value))}
          />
        </header>

        {error ? (
          <Alert
            type="warning"
            showIcon
            message={error}
            action={
              <Button size="small" onClick={() => void load(days, page)}>
                重试
              </Button>
            }
          />
        ) : null}

        <Spin spinning={loading}>
          <div className="flex flex-col gap-20">
            {summary ? <UsageTotalsCards totals={summary.totals} /> : null}
            {summary ? <UsageDailyTrend daily={summary.daily} /> : null}

            <section>
              <h2 className="m-0 mb-8 px-4 text-[13px] font-medium tracking-[0.02em] text-text-tertiary">
                按用户
              </h2>
              <Table<UsageUserRow>
                rowKey="userId"
                size="middle"
                columns={columns}
                dataSource={rows}
                pagination={false}
                locale={{ emptyText: '这段时间还没有调用记录' }}
              />
              <div className="mt-12 flex items-center justify-between">
                <Button
                  size="small"
                  disabled={page <= 1 || loading}
                  onClick={() => void load(days, page - 1)}
                >
                  上一页
                </Button>
                <span className="text-[13px] text-text-tertiary">
                  第 {page} 页
                </span>
                <Button
                  size="small"
                  disabled={!hasMore || loading}
                  onClick={() => void load(days, page + 1)}
                >
                  下一页
                </Button>
              </div>
            </section>
          </div>
        </Spin>
      </div>
    </div>
  );
});

AdminUsagePage.displayName = 'AdminUsagePage';

export default AdminUsagePage;
