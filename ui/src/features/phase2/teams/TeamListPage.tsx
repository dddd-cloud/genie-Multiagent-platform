import { memo, useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Alert, Button, Spin, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { Phase2TeamResponse } from '@/contracts/phase2';
import { listTeams } from '@/services/phase2/teams';
import { phase2ErrorMessage } from '../phase2UiError';

const { Title, Text } = Typography;

const TeamListPage: GenieType.FC = memo(() => {
  const navigate = useNavigate();
  const [items, setItems] = useState<Phase2TeamResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await listTeams();
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

  const columns: ColumnsType<Phase2TeamResponse> = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, row) => (
        <Link to={`/app/teams/${row.id}`}>{name}</Link>
      ),
    },
    {
      title: '主 Agent',
      dataIndex: 'masterAgentName',
      key: 'masterAgentName',
      render: (v: string | null, row) => v || row.masterAgentId,
    },
    {
      title: '子 Agent 数',
      key: 'memberCount',
      width: 120,
      render: (_, row) => row.memberAgentIds.length,
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
          onClick={() => navigate(`/app/teams/${row.id}`)}
        >
          编辑
        </Button>
      ),
    },
  ];

  return (
    <div className="h-full w-full overflow-auto" data-testid="team-list-page">
      <div className="flex items-center justify-between gap-12 mb-16">
        <div>
          <Title level={4} className="!mb-4">
            我的团队
          </Title>
          <Text type="secondary">一个主 Agent 带若干子 Agent 协作完成任务</Text>
        </div>
        <Button type="primary" onClick={() => navigate('/app/teams/new')}>
          新建团队
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
          locale={{ emptyText: '暂无团队' }}
        />
      </Spin>
    </div>
  );
});

TeamListPage.displayName = 'TeamListPage';

export default TeamListPage;
