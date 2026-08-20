import { memo, useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Alert, Button, Spin, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { Phase2McpServerResponse } from '@/contracts/phase2';
import { listMcpServers } from '@/services/phase2/mcp';
import { MCP_LIBRARY_PATH } from '@/features/marketplace/paths';
import { phase2ErrorMessage } from '../phase2UiError';

const { Title, Text } = Typography;

const McpListPage: GenieType.FC = memo(() => {
  const navigate = useNavigate();
  const [items, setItems] = useState<Phase2McpServerResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await listMcpServers();
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

  const columns: ColumnsType<Phase2McpServerResponse> = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, row) => (
        <Link to={`${MCP_LIBRARY_PATH}/${row.id}`}>{name}</Link>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (status: Phase2McpServerResponse['status']) => <Tag>{status}</Tag>,
    },
    {
      title: '认证',
      dataIndex: 'authType',
      key: 'authType',
      width: 140,
    },
    {
      title: '凭据',
      dataIndex: 'credentialConfigured',
      key: 'credentialConfigured',
      width: 100,
      render: (configured: boolean) => (
        <Tag color={configured ? 'blue' : 'default'}>
          {configured ? '已配置' : '未配置'}
        </Tag>
      ),
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
          onClick={() => navigate(`${MCP_LIBRARY_PATH}/${row.id}`)}
        >
          编辑
        </Button>
      ),
    },
  ];

  return (
    <div className="h-full w-full overflow-auto" data-testid="mcp-list-page">
      <div className="flex items-center justify-between gap-12 mb-16">
        <div>
          <Title level={4} className="!mb-4">
            我的连接器
          </Title>
          <Text type="secondary">管理已配置的连接器；凭据不会回传到浏览器</Text>
        </div>
        <Button type="primary" className="rounded-full" onClick={() => navigate(`${MCP_LIBRARY_PATH}/new`)}>
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
        <Table
          rowKey="id"
          columns={columns}
          dataSource={items}
          pagination={false}
          locale={{ emptyText: '还没有连接器' }}
        />
      </Spin>
    </div>
  );
});

McpListPage.displayName = 'McpListPage';

export default McpListPage;
