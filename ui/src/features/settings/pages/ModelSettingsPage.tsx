import { memo, useEffect, useState } from 'react';
import { Alert, Spin, Table, Typography } from 'antd';
import type { Phase2ModelResponse } from '@/contracts/phase2';
import { listModels } from '@/services/phase2/agents';
import { phase2ErrorMessage } from '@/features/phase2/phase2UiError';

const { Paragraph } = Typography;

const ModelSettingsPage: GenieType.FC = memo(() => {
  const [models, setModels] = useState<Phase2ModelResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    listModels(controller.signal)
      .then((items) => {
        setModels(items ?? []);
        setError(null);
      })
      .catch((err: unknown) => {
        if (!controller.signal.aborted) {
          setError(phase2ErrorMessage(err));
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, []);

  return (
    <div data-testid="settings-models">
      <Paragraph type="secondary" className="!mb-16">
        这里只展示当前服务端已配置、可供会话使用的模型目录。供应商凭据由服务端管理，不会出现在浏览器里。
      </Paragraph>
      {error ? <Alert type="warning" showIcon className="mb-16" message={error} /> : null}
      <Spin spinning={loading}>
        <Table
          rowKey="name"
          pagination={false}
          dataSource={models}
          columns={[
            { title: '模型', dataIndex: 'displayName', key: 'displayName' },
            { title: '标识', dataIndex: 'name', key: 'name' },
            {
              title: '状态',
              dataIndex: 'available',
              key: 'available',
              render: (available: boolean) => (available ? '可用' : '不可用'),
            },
          ]}
          locale={{ emptyText: '当前没有可展示的模型' }}
        />
      </Spin>
    </div>
  );
});

ModelSettingsPage.displayName = 'ModelSettingsPage';

export default ModelSettingsPage;
