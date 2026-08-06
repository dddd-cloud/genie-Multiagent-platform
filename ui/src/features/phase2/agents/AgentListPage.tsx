import { memo, useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Alert, Button, Spin, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { Phase2AgentResponse } from '@/contracts/phase2';
import { listAgents } from '@/services/phase2/agents';
import { phase2ErrorMessage } from '../phase2UiError';

const { Title, Text } = Typography;

const AgentListPage: GenieType.FC = memo(() => {
  const navigate = useNavigate();
  const [items, setItems] = useState<Phase2AgentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await listAgents();
      setItems(data ?? []);
    } catch (err: unknown) {
      setError(phase2ErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const columns: ColumnsType<Phase2AgentResponse> = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, row) => (
        <Link to={`/app/agents/${row.id}`}>{name}</Link>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status: Phase2AgentResponse['status']) => (
        <Tag color={status === 'ONLINE' ? 'green' : 'default'}>{status}</Tag>
      ),
    },
    {
      title: '模型',
      dataIndex: 'modelName',
      key: 'modelName',
      render: (v: string | null) => v || '—',
    },
    {
      title: '版本',
      dataIndex: 'version',
      key: 'version',
      width: 80,
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (_, row) => (
        <Button
          type="link"
          size="small"
          onClick={() => navigate(`/app/agents/${row.id}`)}
        >
          编辑
        </Button>
      ),
    },
  ];

  return (
    <div className="h-full w-full overflow-auto p-24" data-testid="agent-list-page">
      <div className="flex items-center justify-between gap-12 mb-16">
        <div>
          <Title level={4} className="!mb-4">
            Agent
          </Title>
          <Text type="secondary">管理可配置 Agent</Text>
        </div>
        <Button type="primary" onClick={() => navigate('/app/agents/new')}>
          新建 Agent
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
        <Table
          rowKey="id"
          columns={columns}
          dataSource={items}
          pagination={false}
          locale={{ emptyText: '暂无 Agent' }}
        />
      </Spin>
    </div>
  );
});

AgentListPage.displayName = 'AgentListPage';

export default AgentListPage;
