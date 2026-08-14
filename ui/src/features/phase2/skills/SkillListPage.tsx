import { memo, useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Alert, Button, Space, Spin, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { Phase2SkillResponse } from '@/contracts/phase2';
import {
  disableSkill,
  enableSkill,
  listSkills,
} from '@/services/phase2/skills';
import {
  isVersionConflict,
  phase2ErrorMessage,
} from '../phase2UiError';
import SkillImportModal from './SkillImportModal';

const { Title, Text } = Typography;

const SkillListPage: GenieType.FC = memo(() => {
  const navigate = useNavigate();
  const [items, setItems] = useState<Phase2SkillResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [importOpen, setImportOpen] = useState(false);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await listSkills();
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

  const toggle = async (row: Phase2SkillResponse) => {
    setBusyId(row.id);
    try {
      const updated =
        row.status === 'ENABLED'
          ? await disableSkill(row.id, { version: row.version })
          : await enableSkill(row.id, { version: row.version });
      if (updated) {
        setItems((prev) =>
          prev.map((item) => (item.id === updated.id ? updated : item)),
        );
      }
      message.success(row.status === 'ENABLED' ? '已禁用' : '已启用');
    } catch (err: unknown) {
      if (isVersionConflict(err)) {
        message.error(phase2ErrorMessage(err));
        void reload();
        return;
      }
      message.error(phase2ErrorMessage(err));
    } finally {
      setBusyId(null);
    }
  };

  const columns: ColumnsType<Phase2SkillResponse> = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, row) => (
        <Link to={`/app/skills/${row.id}`}>{name}</Link>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status: Phase2SkillResponse['status']) => (
        <Tag color={status === 'ENABLED' ? 'green' : 'default'}>{status}</Tag>
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
      width: 180,
      render: (_, row) => (
        <>
          <Button
            type="link"
            size="small"
            loading={busyId === row.id}
            onClick={() => void toggle(row)}
            data-testid={`skill-toggle-${row.id}`}
          >
            {row.status === 'ENABLED' ? '禁用' : '启用'}
          </Button>
          <Button
            type="link"
            size="small"
            onClick={() => navigate(`/app/skills/${row.id}`)}
          >
            编辑
          </Button>
        </>
      ),
    },
  ];

  return (
    <div className="h-full w-full overflow-auto p-24" data-testid="skill-list-page">
      <div className="flex items-center justify-between gap-12 mb-16">
        <div>
          <Title level={4} className="!mb-4">
            Skill
          </Title>
          <Text type="secondary">管理 Skill；排序仅在 Agent 编辑页</Text>
        </div>
        <Space>
          <Button onClick={() => navigate('/app/skills/new')}>新建 Skill</Button>
          <Button
            type="primary"
            onClick={() => setImportOpen(true)}
            data-testid="skill-import-open"
          >
            导入包
          </Button>
        </Space>
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
          locale={{ emptyText: '暂无 Skill' }}
        />
      </Spin>
      <SkillImportModal
        open={importOpen}
        onClose={() => setImportOpen(false)}
        onImported={(skill) => {
          setImportOpen(false);
          navigate(`/app/skills/${skill.id}`);
        }}
      />
    </div>
  );
});

SkillListPage.displayName = 'SkillListPage';

export default SkillListPage;
