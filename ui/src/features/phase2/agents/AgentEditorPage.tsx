import { memo, useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Modal, Space, Spin, Typography, message } from 'antd';
import type {
  Phase2ModelResponse,
  Phase2SkillResponse,
} from '@/contracts/phase2';
import type { ToolCapabilityItem } from '@/services/phase2/internalTypes';
import {
  createAgent,
  deleteAgent,
  getAgent,
  listModels,
  listToolCapabilities,
  offlineAgent,
  onlineAgent,
  updateAgent,
} from '@/services/phase2/agents';
import { listSkills } from '@/services/phase2/skills';
import VersionConflictAlert from '../VersionConflictAlert';
import {
  isVersionConflict,
  phase2ErrorMessage,
} from '../phase2UiError';
import AgentForm from './AgentForm';
import AgentTestModal from './AgentTestModal';
import {
  agentToFormState,
  emptyAgentFormState,
  formStateToCreateRequest,
  formStateToUpdateRequest,
  validateAgentForm,
  type AgentFormState,
} from './agentFormModel';

const { Title, Text } = Typography;

const AgentEditorPage: GenieType.FC = memo(() => {
  const { agentId } = useParams<{ agentId?: string }>();
  const isNew = !agentId || agentId === 'new';
  const navigate = useNavigate();

  const [form, setForm] = useState<AgentFormState>(emptyAgentFormState());
  const [models, setModels] = useState<Phase2ModelResponse[]>([]);
  const [skills, setSkills] = useState<Phase2SkillResponse[]>([]);
  const [capabilities, setCapabilities] = useState<ToolCapabilityItem[]>([]);
  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [versionConflict, setVersionConflict] = useState(false);
  const [testOpen, setTestOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<{
    id: string;
    version: number;
  } | null>(null);

  const loadMeta = useCallback(async (signal?: AbortSignal) => {
    const [modelList, skillList, capList] = await Promise.all([
      listModels(signal),
      listSkills(signal),
      listToolCapabilities(signal),
    ]);
    setModels(modelList ?? []);
    setSkills(skillList ?? []);
    setCapabilities(capList ?? []);
  }, []);

  const loadAgent = useCallback(
    async (signal?: AbortSignal) => {
      if (isNew || !agentId) {
        setForm(emptyAgentFormState());
        setVersionConflict(false);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const agent = await getAgent(agentId, signal);
        if (!agent) {
          setError('Agent 不存在');
          return;
        }
        setForm(agentToFormState(agent));
        setVersionConflict(false);
      } catch (err: unknown) {
        setError(phase2ErrorMessage(err));
      } finally {
        setLoading(false);
      }
    },
    [agentId, isNew],
  );

  useEffect(() => {
    const controller = new AbortController();
    void (async () => {
      try {
        await loadMeta(controller.signal);
        await loadAgent(controller.signal);
      } catch (err: unknown) {
        if (!controller.signal.aborted) {
          setError(phase2ErrorMessage(err));
          setLoading(false);
        }
      }
    })();
    return () => controller.abort();
  }, [loadAgent, loadMeta]);

  const handleSave = async () => {
    const validation = validateAgentForm(form);
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
        const created = await createAgent(formStateToCreateRequest(form));
        if (!created) {
          message.error('创建失败');
          return;
        }
        message.success('已创建');
        navigate(`/app/agents/${created.id}`, { replace: true });
        return;
      }
      if (!agentId) return;
      const updated = await updateAgent(
        agentId,
        formStateToUpdateRequest(form),
      );
      if (!updated) {
        message.error('保存失败');
        return;
      }
      setForm(agentToFormState(updated));
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
    action: typeof onlineAgent | typeof offlineAgent,
    successText: string,
  ) => {
    if (!agentId || form.version == null) return;
    if (versionConflict) {
      message.warning('请先重新加载服务器版本');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const updated = await action(agentId, { version: form.version });
      if (updated) {
        setForm(agentToFormState(updated));
      }
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

  const handleDelete = () => {
    if (form.status === 'ONLINE') {
      message.warning('ONLINE 状态不可删除，请先下线');
      return;
    }
    if (versionConflict) {
      message.warning('请先重新加载服务器版本');
      return;
    }
    if (!agentId) {
      setError('Agent 标识缺失，请返回列表后重新进入');
      return;
    }
    if (form.version == null) {
      setError('Agent 详情尚未加载完成，请稍候重试');
      return;
    }
    setDeleteTarget({ id: agentId, version: form.version });
  };

  const confirmDelete = async () => {
    if (!deleteTarget) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await deleteAgent(deleteTarget.id, { version: deleteTarget.version });
      setDeleteTarget(null);
      message.success('已删除');
      navigate('/app/agents');
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

  const isOnline = form.status === 'ONLINE';

  return (
    <div className="h-full w-full overflow-auto p-24" data-testid="agent-editor-page">
      <div className="flex items-center justify-between gap-12 mb-16">
        <div>
          <Title level={4} className="!mb-4">
            {isNew ? '新建 Agent' : '编辑 Agent'}
          </Title>
          <Text type="secondary">
            {isNew ? '创建后默认为 DRAFT' : `ID: ${agentId}`}
          </Text>
        </div>
        <Space wrap>
          <Button onClick={() => navigate('/app/agents')}>返回列表</Button>
          {!isNew && isOnline ? (
            <Button onClick={() => setTestOpen(true)} data-testid="agent-test-open">
              测试
            </Button>
          ) : null}
          {!isNew && form.status !== 'ONLINE' ? (
            <Button
              onClick={() => void runLifecycle(onlineAgent, '已上线')}
              disabled={versionConflict || saving}
            >
              上线
            </Button>
          ) : null}
          {!isNew && form.status === 'ONLINE' ? (
            <Button
              onClick={() => void runLifecycle(offlineAgent, '已下线')}
              disabled={versionConflict || saving}
            >
              下线
            </Button>
          ) : null}
          <Button
            type="primary"
            loading={saving}
            disabled={versionConflict}
            onClick={() => void handleSave()}
            data-testid="agent-save"
          >
            保存
          </Button>
          {!isNew ? (
            <Button
              danger
              disabled={isOnline || versionConflict || saving}
              onClick={handleDelete}
              data-testid="agent-delete"
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
            onReload={() => void loadAgent()}
          />
        </div>
      ) : null}

      {error && !versionConflict ? (
        <Alert type="error" showIcon className="mb-16" message={error} />
      ) : null}

      <Spin spinning={loading}>
        <div className="max-w-[720px]">
          <AgentForm
            value={form}
            onChange={setForm}
            models={models}
            skills={skills}
            capabilities={capabilities}
            disabled={saving}
            readOnly={versionConflict}
          />
        </div>
      </Spin>

      {!isNew && agentId ? (
        <AgentTestModal
          open={testOpen}
          agentId={agentId}
          agentName={form.name || agentId}
          onClose={() => setTestOpen(false)}
        />
      ) : null}

      <Modal
        title="确认删除该 Agent？"
        open={deleteTarget !== null}
        okText="删除"
        cancelText="取消"
        okType="danger"
        confirmLoading={saving}
        maskClosable={!saving}
        closable={!saving}
        onOk={() => void confirmDelete()}
        onCancel={() => {
          if (!saving) {
            setDeleteTarget(null);
          }
        }}
      >
        <Text>下线后的 Agent 将被删除，此操作不可恢复。</Text>
      </Modal>
    </div>
  );
});

AgentEditorPage.displayName = 'AgentEditorPage';

export default AgentEditorPage;
