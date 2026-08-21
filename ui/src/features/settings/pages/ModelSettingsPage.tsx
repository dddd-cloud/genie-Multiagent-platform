import { memo, useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert, Button, Spin } from 'antd';
import type { Phase2ModelResponse } from '@/contracts/phase2';
import { listModels } from '@/services/phase2/models';
import { phase2ErrorMessage } from '@/features/phase2/phase2UiError';

function modelKey(item: Phase2ModelResponse) {
  return item.id || item.name;
}

const ModelSettingsPage: GenieType.FC = memo(() => {
  const navigate = useNavigate();
  const [models, setModels] = useState<Phase2ModelResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    setError(null);
    try {
      const items = (await listModels(signal)) ?? [];
      setModels(items.filter((item) => item.name !== 'system-default'));
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
    <div data-testid="settings-models">
      <div className="mb-16 flex items-start justify-between gap-12">
        <p className="m-0 text-[13px] leading-[20px] text-text-secondary">
          管理可用模型。API Key 仅保存在服务端。
        </p>
        <Button
          type="primary"
          className="rounded-full"
          data-testid="settings-models-new"
          onClick={() => navigate('/app/settings/models/new')}
        >
          新建模型
        </Button>
      </div>
      {error ? (
        <Alert
          type="warning"
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
        {models.length === 0 && !loading ? (
          <div className="rounded-xl bg-surface px-16 py-28 text-center text-[14px] text-text-tertiary shadow-xs">
            还没有模型。点击右上角新建一个。
          </div>
        ) : (
          <div className="overflow-hidden rounded-xl bg-surface shadow-xs">
            {models.map((item, index) => (
              <button
                key={modelKey(item)}
                type="button"
                data-testid={`settings-model-row-${item.name}`}
                className={[
                  'flex w-full items-center justify-between gap-12 border-0 bg-transparent px-16 py-14 text-left transition-colors hover:bg-[#F5F5F7]',
                  index === models.length - 1
                    ? ''
                    : 'border-b border-solid border-border',
                ].join(' ')}
                onClick={() =>
                  navigate(
                    `/app/settings/models/${encodeURIComponent(modelKey(item))}`,
                  )
                }
              >
                <div className="min-w-0">
                  <div className="truncate text-[15px] text-text-primary">
                    {item.displayName || item.name}
                  </div>
                  <div className="mt-2 truncate text-[12px] text-text-tertiary">
                    {item.name}
                    {item.isDefault ? ' · 默认' : ''}
                    {item.available ? '' : ' · 未完成'}
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

ModelSettingsPage.displayName = 'ModelSettingsPage';

export default ModelSettingsPage;
