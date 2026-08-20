import { memo, useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert, Button, Spin } from 'antd';
import type { Phase2AgentResponse } from '@/contracts/phase2';
import { listAgents } from '@/services/phase2/agents';
import { phase2ErrorMessage } from '../phase2UiError';
import { agentStatusLabel } from './agentFormModel';

const AgentListPage: GenieType.FC = memo(() => {
  const navigate = useNavigate();
  const [items, setItems] = useState<Phase2AgentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    setError(null);
    try {
      setItems((await listAgents(signal)) ?? []);
    } catch (err: unknown) {
      if (!signal?.aborted) {
        setError(phase2ErrorMessage(err));
      }
    } finally {
      if (!signal?.aborted) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void reload(controller.signal);
    return () => controller.abort();
  }, [reload]);

  return (
    <div data-testid="agent-list-page">
      <div className="mb-16 flex items-start justify-between gap-12">
        <p className="m-0 text-[13px] leading-[20px] text-text-secondary">
          上线后可在对话中选用。
        </p>
        <Button
          type="primary"
          className="rounded-full"
          onClick={() => navigate('/app/settings/agents/new')}
        >
          新建
        </Button>
      </div>
      {error ? (
        <Alert
          type="error"
          showIcon
          className="mb-16"
          message={error}
          action={
            <Button size="small" onClick={() => void reload()}>
              重试
            </Button>
          }
        />
      ) : null}
      <Spin spinning={loading}>
        {items.length === 0 && !loading ? (
          <div className="rounded-xl bg-surface px-16 py-28 text-center text-[14px] text-text-tertiary shadow-xs">
            还没有智能体。点击右上角新建一个。
          </div>
        ) : (
          <div className="overflow-hidden rounded-xl bg-surface shadow-xs">
            {items.map((item, index) => (
              <button
                key={item.id}
                type="button"
                data-testid={`settings-agent-row-${item.id}`}
                className={[
                  'flex w-full items-center justify-between gap-12 border-0 bg-transparent px-16 py-14 text-left transition-colors hover:bg-[#F5F5F7]',
                  index === items.length - 1 ? '' : 'border-b border-solid border-border',
                ].join(' ')}
                onClick={() =>
                  navigate(`/app/settings/agents/${encodeURIComponent(item.id)}`)
                }
              >
                <div className="min-w-0">
                  <div className="truncate text-[15px] text-text-primary">{item.name}</div>
                  <div className="mt-2 truncate text-[12px] text-text-tertiary">
                    {[item.description?.trim(), agentStatusLabel(item.status)]
                      .filter(Boolean)
                      .join(' · ')}
                  </div>
                </div>
                <span className="shrink-0 text-[18px] text-text-tertiary" aria-hidden>
                  ›
                </span>
              </button>
            ))}
          </div>
        )}
      </Spin>
    </div>
  );
});

AgentListPage.displayName = 'AgentListPage';

export default AgentListPage;
