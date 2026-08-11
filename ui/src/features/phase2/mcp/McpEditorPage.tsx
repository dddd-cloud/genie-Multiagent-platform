import { memo, useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Modal, Space, Spin, Typography, message } from 'antd';
import type { Phase2McpToolResponse } from '@/contracts/phase2';
import type {
  McpServerCreateRequest,
  McpServerUpdateRequest,
} from '@/services/phase2/internalTypes';
import {
  createMcpServer,
  deleteMcpServer,
  disableMcpServer,
  enableMcpServer,
  getMcpServer,
  listMcpTools,
  refreshMcpTools,
  testMcpServer,
  updateMcpServer,
} from '@/services/phase2/mcp';
import VersionConflictAlert from '../VersionConflictAlert';
import {
  isVersionConflict,
  phase2ErrorMessage,
} from '../phase2UiError';
import McpForm, {
  emptyMcpFormState,
  serverToFormState,
  validateMcpForm,
  type McpFormState,
} from './McpForm';
import McpToolTable from './McpToolTable';

const { Title, Text } = Typography;

const McpEditorPage: GenieType.FC = memo(() => {
  const { serverId } = useParams<{ serverId?: string }>();
  const isNew = !serverId || serverId === 'new';
  const navigate = useNavigate();

  const [form, setForm] = useState<McpFormState>(emptyMcpFormState());
  const [credential, setCredential] = useState('');
  const [tools, setTools] = useState<Phase2McpToolResponse[]>([]);
  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [versionConflict, setVersionConflict] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<{
    id: string;
    version: number;
  } | null>(null);

  const clearCredential = useCallback(() => {
    setCredential('');
  }, []);

  useEffect(() => {
    return () => {
      setCredential('');
    };
  }, []);

  const loadServer = useCallback(
    async (signal?: AbortSignal) => {
      if (isNew || !serverId) {
        setForm(emptyMcpFormState());
        setTools([]);
        setVersionConflict(false);
        clearCredential();
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const [server, toolList] = await Promise.all([
          getMcpServer(serverId, signal),
          listMcpTools(serverId, signal),
        ]);
        if (!server) {
          setError('MCP Server 不存在');
          return;
        }
        setForm(serverToFormState(server));
        setTools(toolList ?? []);
        setVersionConflict(false);
        clearCredential();
      } catch (err: unknown) {
        setError(phase2ErrorMessage(err));
      } finally {
        setLoading(false);
      }
    },
    [clearCredential, isNew, serverId],
  );

  useEffect(() => {
    const controller = new AbortController();
    void loadServer(controller.signal);
    return () => controller.abort();
  }, [loadServer]);

  const buildCreateBody = (): McpServerCreateRequest => {
    const body: McpServerCreateRequest = {
      name: form.name.trim(),
      serverUrl: form.serverUrl.trim(),
      authType: form.authType,
      authName:
        form.authType === 'NONE' ? null : form.authName.trim() || null,
    };
    if (
      (form.authType === 'BEARER_TOKEN' || form.authType === 'QUERY_PARAM') &&
      credential
    ) {
      body.credential = credential;
    }
    return body;
  };

  const buildUpdateBody = (): McpServerUpdateRequest => {
    if (form.version == null) {
      throw new Error('缺少版本号');
    }
    const body: McpServerUpdateRequest = {
      name: form.name.trim(),
      serverUrl: form.serverUrl.trim(),
      authType: form.authType,
      authName:
        form.authType === 'NONE' ? null : form.authName.trim() || null,
      version: form.version,
    };
    if (form.clearCredential) {
      body.clearCredential = true;
    } else if (
      (form.authType === 'BEARER_TOKEN' || form.authType === 'QUERY_PARAM') &&
      credential
    ) {
      body.credential = credential;
    }
    return body;
  };

  const handleSave = async () => {
    const validation = validateMcpForm(form);
    if (validation) {
      message.error(validation);
      return;
    }
    if (versionConflict) {
      message.warning('请先重新加载服务器版本');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      if (isNew) {
        const created = await createMcpServer(buildCreateBody());
        clearCredential();
        if (!created) {
          message.error('创建失败');
          return;
        }
        message.success('已创建');
        navigate(`/app/mcp/${created.id}`, { replace: true });
        return;
      }
      if (!serverId) return;
      const updated = await updateMcpServer(serverId, buildUpdateBody());
      clearCredential();
      if (!updated) {
        message.error('保存失败');
        return;
      }
      setForm(serverToFormState(updated));
      message.success('已保存');
    } catch (err: unknown) {
      if (isVersionConflict(err)) {
        setVersionConflict(true);
        setError(phase2ErrorMessage(err));
        return;
      }
      setError(phase2ErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const runLifecycle = async (
    action: typeof enableMcpServer | typeof disableMcpServer,
    successText: string,
  ) => {
    if (!serverId || form.version == null) return;
    if (versionConflict) {
      message.warning('请先重新加载服务器版本');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const updated = await action(serverId, { version: form.version });
      if (updated) setForm(serverToFormState(updated));
      message.success(successText);
    } catch (err: unknown) {
      if (isVersionConflict(err)) {
        setVersionConflict(true);
        setError(phase2ErrorMessage(err));
        return;
      }
      setError(phase2ErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const handleTest = async () => {
    if (!serverId || form.version == null) return;
    if (versionConflict) {
      message.warning('请先重新加载服务器版本');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const updated = await testMcpServer(serverId);
      clearCredential();
      if (updated) setForm(serverToFormState(updated));
      message.success('测试完成');
    } catch (err: unknown) {
      clearCredential();
      if (isVersionConflict(err)) {
        setVersionConflict(true);
        setError(phase2ErrorMessage(err));
        return;
      }
      setError(phase2ErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const handleRefreshTools = async () => {
    if (!serverId || form.version == null) return;
    if (versionConflict) {
      message.warning('请先重新加载服务器版本');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const refreshed = await refreshMcpTools(serverId);
      setTools(refreshed ?? []);
      const server = await getMcpServer(serverId);
      if (server) setForm(serverToFormState(server));
      message.success('工具列表已刷新');
    } catch (err: unknown) {
      if (isVersionConflict(err)) {
        setVersionConflict(true);
        setError(phase2ErrorMessage(err));
        return;
      }
      setError(phase2ErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = () => {
    if (!serverId) {
      setError('MCP Server 标识缺失，请返回列表后重新进入');
      return;
    }
    if (versionConflict) {
      message.warning('请先重新加载服务器版本');
      return;
    }
    if (form.version == null) {
      setError('MCP Server 详情尚未加载完成，请稍候重试');
      return;
    }
    setDeleteTarget({ id: serverId, version: form.version });
  };

  const confirmDelete = async () => {
    if (!deleteTarget) return;
    setSaving(true);
    setError(null);
    try {
      await deleteMcpServer(deleteTarget.id, { version: deleteTarget.version });
      clearCredential();
      setDeleteTarget(null);
      message.success('已删除');
      navigate('/app/mcp');
    } catch (err: unknown) {
      if (isVersionConflict(err)) {
        setVersionConflict(true);
        setDeleteTarget(null);
        setError(phase2ErrorMessage(err));
        return;
      }
      setError(phase2ErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="h-full w-full overflow-auto p-24" data-testid="mcp-editor-page">
      <div className="flex items-center justify-between gap-12 mb-16">
        <div>
          <Title level={4} className="!mb-4">
            {isNew ? '新建 MCP' : '编辑 MCP'}
          </Title>
          <Text type="secondary">
            {isNew ? '配置后可测试并发现工具' : `ID: ${serverId}`}
          </Text>
        </div>
        <Space wrap>
          <Button onClick={() => navigate('/app/mcp')}>返回列表</Button>
          {!isNew ? (
            <Button
              disabled={versionConflict || saving}
              onClick={() => void handleTest()}
              data-testid="mcp-test"
            >
              测试连接
            </Button>
          ) : null}
          {!isNew ? (
            <Button
              disabled={versionConflict || saving}
              onClick={() => void handleRefreshTools()}
              data-testid="mcp-refresh-tools"
            >
              刷新工具
            </Button>
          ) : null}
          {!isNew && form.status !== 'ENABLED' ? (
            <Button
              disabled={versionConflict || saving}
              onClick={() => void runLifecycle(enableMcpServer, '已启用')}
            >
              启用
            </Button>
          ) : null}
          {!isNew && form.status === 'ENABLED' ? (
            <Button
              disabled={versionConflict || saving}
              onClick={() => void runLifecycle(disableMcpServer, '已禁用')}
            >
              禁用
            </Button>
          ) : null}
          <Button
            type="primary"
            loading={saving}
            disabled={versionConflict}
            onClick={() => void handleSave()}
            data-testid="mcp-save"
          >
            保存
          </Button>
          {!isNew ? (
            <Button
              danger
              disabled={versionConflict || saving}
              onClick={handleDelete}
              data-testid="mcp-delete"
            >
              删除
            </Button>
          ) : null}
        </Space>
      </div>

      {versionConflict ? (
        <div className="mb-16">
          <VersionConflictAlert
            disabled={loading}
            onReload={() => void loadServer()}
          />
        </div>
      ) : null}

      {error && !versionConflict ? (
        <Alert
          type="error"
          showIcon
          className="mb-16"
          message={error}
          data-testid="mcp-error"
        />
      ) : null}

      <Spin spinning={loading}>
        <div className="max-w-[800px] flex flex-col gap-20">
          <McpForm
            value={form}
            onChange={setForm}
            credential={credential}
            onCredentialChange={setCredential}
            disabled={saving}
            readOnly={versionConflict}
            isNew={isNew}
          />
          {!isNew && serverId ? (
            <div>
              <Text strong className="mb-10 block">
                工具列表
              </Text>
              <McpToolTable
                serverId={serverId}
                tools={tools}
                onToolsChange={setTools}
                disabled={versionConflict || saving}
              />
            </div>
          ) : null}
        </div>
      </Spin>
      <Modal
        title="确认删除该 MCP Server？"
        open={deleteTarget !== null}
        okText="删除"
        cancelText="取消"
        okType="danger"
        confirmLoading={saving}
        maskClosable={!saving}
        closable={!saving}
        onOk={() => void confirmDelete()}
        onCancel={() => {
          if (!saving) setDeleteTarget(null);
        }}
      >
        <Text>删除后，已发现的 MCP 工具将同时停用。</Text>
      </Modal>
    </div>
  );
});

McpEditorPage.displayName = 'McpEditorPage';

export default McpEditorPage;
