import { memo, useCallback, useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Modal, Spin, message } from 'antd';
import type { Phase2SkillResponse } from '@/contracts/phase2';
import type { ToolCapabilityItem } from '@/services/phase2/internalTypes';
import {
  createAgent,
  deleteAgent,
  getAgent,
  listToolCapabilities,
  offlineAgent,
  onlineAgent,
  updateAgent,
} from '@/services/phase2/agents';
import { listSkills } from '@/services/phase2/skills';
import VersionConflictAlert from '../VersionConflictAlert';
import { isVersionConflict, phase2ErrorMessage } from '../phase2UiError';
import AgentForm from './AgentForm';
import {
  agentStatusLabel,
  agentToFormState,
  emptyAgentFormState,
  formStateToCreateRequest,
  formStateToUpdateRequest,
  validateAgentForm,
  type AgentFormState,
} from './agentFormModel';

function isAgentDraft(value: unknown): value is AgentFormState {
  return Boolean(value && typeof value === 'object' && 'skillIds' in (value as object));
}

const AgentEditorPage: GenieType.FC = memo(() => {
  const { agentId } = useParams<{ agentId?: string }>();
  const isNew = !agentId || agentId === 'new';
  const navigate = useNavigate();
  const location = useLocation();
  const locationDraft = (location.state as { draft?: unknown } | null)?.draft;

  const [form, setForm] = useState<AgentFormState>(emptyAgentFormState());
  const [skills, setSkills] = useState<Phase2SkillResponse[]>([]);
  const [capabilities, setCapabilities] = useState<ToolCapabilityItem[]>([]);
  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [versionConflict, setVersionConflict] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<{
    id: string;
    version: number;
  } | null>(null);

  const loadMeta = useCallback(async (signal?: AbortSignal) => {
    const [skillList, capList] = await Promise.all([
      listSkills(signal),
      listToolCapabilities(signal),
    ]);
    setSkills(skillList ?? []);
    setCapabilities(capList ?? []);
  }, []);

  const loadAgent = useCallback(
    async (signal?: AbortSignal) => {
      if (isNew || !agentId) {
        setForm(isAgentDraft(locationDraft) ? locationDraft : emptyAgentFormState());
        setVersionConflict(false);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const agent = await getAgent(agentId, signal);
        if (!agent) {
          setError('找不到这个智能体');
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
    [agentId, isNew, locationDraft],
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
      message.warning('请先重新加载');
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
        navigate(`/app/settings/agents/${created.id}`, { replace: true });
        return;
      }
      if (!agentId) return;
      const updated = await updateAgent(agentId, formStateToUpdateRequest(form));
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
      message.warning('请先重新加载');
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
    if (versionConflict) {
      message.warning('请先重新加载');
      return;
    }
    if (!agentId) {
      setError('无法删除，请返回列表后重新进入');
      return;
    }
    if (form.version == null) {
      setError('还没加载完成，请稍后再试');
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
      navigate('/app/settings/agents');
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

  const statusLabel = agentStatusLabel(form.status);

  return (
    <div data-testid="agent-editor-page">
      <button
        type="button"
        className="mb-12 border-0 bg-transparent p-0 text-[13px] text-text-secondary hover:text-text-primary"
        onClick={() => navigate('/app/settings/agents')}
      >
        ‹ 智能体
      </button>
      <div className="mb-16 flex items-start justify-between gap-12">
        <div className="min-w-0">
          <h2 className="m-0 text-[20px] font-semibold tracking-[-0.02em] text-text-primary">
            {isNew ? '新建智能体' : form.name || '智能体'}
          </h2>
          {statusLabel ? (
            <div className="mt-4 text-[13px] text-text-secondary">{statusLabel}</div>
          ) : null}
        </div>
        <div className="flex shrink-0 flex-wrap items-center justify-end gap-8">
          {!isNew ? (
            <Button
              danger
              disabled={versionConflict || saving || form.version == null}
              onClick={handleDelete}
              data-testid="agent-delete"
            >
              删除
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
            className="rounded-full"
            loading={saving}
            disabled={versionConflict}
            onClick={() => void handleSave()}
            data-testid="agent-save"
          >
            保存
          </Button>
        </div>
      </div>

      {versionConflict ? (
        <div className="mb-16">
          <VersionConflictAlert disabled={loading} onReload={() => void loadAgent()} />
        </div>
      ) : null}

      {error && !versionConflict ? (
        <Alert type="error" showIcon className="mb-16" message={error} />
      ) : null}

      <Spin spinning={loading}>
        <AgentForm
          value={form}
          onChange={setForm}
          skills={skills}
          capabilities={capabilities}
          disabled={saving}
          readOnly={versionConflict}
        />
      </Spin>

      <Modal
        title="删除这个智能体？"
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
        删除后无法恢复。
      </Modal>
    </div>
  );
});

AgentEditorPage.displayName = 'AgentEditorPage';

export default AgentEditorPage;
