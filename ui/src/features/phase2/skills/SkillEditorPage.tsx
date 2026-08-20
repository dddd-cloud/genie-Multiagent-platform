import { memo, useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Modal, Space, Spin, Typography, message } from 'antd';
import type { Phase2AgentResponse } from '@/contracts/phase2';
import type { ToolCapabilityItem } from '@/services/phase2/internalTypes';
import { listAgents, listToolCapabilities } from '@/services/phase2/agents';
import { SKILLS_LIBRARY_PATH } from '@/features/marketplace/paths';
import {
  createSkill,
  deleteSkill,
  disableSkill,
  enableSkill,
  getSkill,
  updateSkill,
} from '@/services/phase2/skills';
import VersionConflictAlert from '../VersionConflictAlert';
import {
  isSkillInUse,
  isVersionConflict,
  phase2ErrorMessage,
} from '../phase2UiError';
import SkillForm, {
  emptySkillFormState,
  skillToFormState,
  validateSkillForm,
  type SkillFormState,
} from './SkillForm';
import SkillPackageInfoPanel from './SkillPackageInfoPanel';
import SkillUsagePanel from './SkillUsagePanel';

const { Title, Text } = Typography;

const SkillEditorPage: GenieType.FC = memo(() => {
  const { skillId } = useParams<{ skillId?: string }>();
  const isNew = !skillId || skillId === 'new';
  const navigate = useNavigate();

  const [form, setForm] = useState<SkillFormState>(emptySkillFormState());
  const [skillRaw, setSkillRaw] = useState<unknown>(null);
  const [capabilities, setCapabilities] = useState<ToolCapabilityItem[]>([]);
  const [agents, setAgents] = useState<Phase2AgentResponse[]>([]);
  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [versionConflict, setVersionConflict] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<{
    id: string;
    version: number;
  } | null>(null);

  const loadSkill = useCallback(
    async (signal?: AbortSignal) => {
      if (isNew || !skillId) {
        setForm(emptySkillFormState());
        setSkillRaw(null);
        setVersionConflict(false);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const skill = await getSkill(skillId, signal);
        if (!skill) {
          setError('Skill 不存在');
          return;
        }
        setForm(skillToFormState(skill));
        setSkillRaw(skill);
        setVersionConflict(false);
      } catch (err: unknown) {
        setError(phase2ErrorMessage(err));
      } finally {
        setLoading(false);
      }
    },
    [isNew, skillId],
  );

  useEffect(() => {
    const controller = new AbortController();
    void (async () => {
      try {
        const [caps, agentList] = await Promise.all([
          listToolCapabilities(controller.signal),
          listAgents(controller.signal),
        ]);
        setCapabilities(caps ?? []);
        setAgents(agentList ?? []);
        await loadSkill(controller.signal);
      } catch (err: unknown) {
        if (!controller.signal.aborted) {
          setError(phase2ErrorMessage(err));
          setLoading(false);
        }
      }
    })();
    return () => controller.abort();
  }, [loadSkill]);

  const handleSave = async () => {
    const validation = validateSkillForm(form);
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
        const created = await createSkill({
          name: form.name.trim(),
          description: (form.description ?? '').trim(),
          instruction: form.instruction.trim(),
          outputRequirement: (form.outputRequirement ?? '').trim(),
          capabilityKeys: [...form.capabilityKeys],
        });
        if (!created) {
          message.error('创建失败');
          return;
        }
        message.success('已创建');
        navigate(`${SKILLS_LIBRARY_PATH}/${created.id}`, { replace: true });
        return;
      }
      if (!skillId || form.version == null) return;
      const updated = await updateSkill(skillId, {
        name: form.name.trim(),
        description: (form.description ?? '').trim(),
        instruction: form.instruction.trim(),
        outputRequirement: (form.outputRequirement ?? '').trim(),
        capabilityKeys: [...form.capabilityKeys],
        version: form.version,
      });
      if (!updated) {
        message.error('保存失败');
        return;
      }
      setForm(skillToFormState(updated));
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
    action: typeof enableSkill | typeof disableSkill,
    successText: string,
  ) => {
    if (!skillId || form.version == null) return;
    if (versionConflict) {
      message.warning('请先重新加载服务器版本');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const updated = await action(skillId, { version: form.version });
      if (updated) {
        setForm(skillToFormState(updated));
        setSkillRaw(updated);
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
      message.warning('请先重新加载服务器版本');
      return;
    }
    if (!skillId) {
      setError('Skill 标识缺失，请返回列表后重新进入');
      return;
    }
    if (form.version == null) {
      setError('Skill 详情尚未加载完成，请稍候重试');
      return;
    }
    setDeleteTarget({ id: skillId, version: form.version });
  };

  const confirmDelete = async () => {
    if (!deleteTarget) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await deleteSkill(deleteTarget.id, { version: deleteTarget.version });
      setDeleteTarget(null);
      message.success('已删除');
      navigate(SKILLS_LIBRARY_PATH);
    } catch (err: unknown) {
      if (isSkillInUse(err)) {
        setError(phase2ErrorMessage(err));
        message.error(phase2ErrorMessage(err));
        return;
      }
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
    <div className="h-full w-full overflow-auto" data-testid="skill-editor-page">
      <div className="flex items-center justify-between gap-12 mb-16">
        <div>
          <Title level={4} className="!mb-4">
            {isNew ? '新建 Skill' : '编辑 Skill'}
          </Title>
          <Text type="secondary">
            {isNew ? '创建后默认为 ENABLED' : `ID: ${skillId}`}
          </Text>
        </div>
        <Space wrap>
          <Button onClick={() => navigate(SKILLS_LIBRARY_PATH)}>返回列表</Button>
          {!isNew && form.status === 'ENABLED' ? (
            <Button
              disabled={versionConflict || saving}
              onClick={() => void runLifecycle(disableSkill, '已禁用')}
            >
              禁用
            </Button>
          ) : null}
          {!isNew && form.status === 'DISABLED' ? (
            <Button
              disabled={versionConflict || saving}
              onClick={() => void runLifecycle(enableSkill, '已启用')}
            >
              启用
            </Button>
          ) : null}
          <Button
            type="primary"
            loading={saving}
            disabled={versionConflict}
            onClick={() => void handleSave()}
            data-testid="skill-save"
          >
            保存
          </Button>
          {!isNew ? (
            <Button
              danger
              disabled={versionConflict || saving}
              onClick={handleDelete}
              data-testid="skill-delete"
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
            onReload={() => void loadSkill()}
          />
        </div>
      ) : null}

      {error && !versionConflict ? (
        <Alert type="error" showIcon className="mb-16" message={error} />
      ) : null}

      <Spin spinning={loading}>
        <div className="max-w-[720px] flex flex-col gap-16">
          {!isNew ? <SkillPackageInfoPanel skill={skillRaw ?? form} /> : null}
          <SkillForm
            value={form}
            onChange={setForm}
            capabilities={capabilities}
            disabled={saving}
            readOnly={versionConflict}
          />
          {!isNew && skillId ? (
            <SkillUsagePanel skillId={skillId} agents={agents} />
          ) : null}
        </div>
      </Spin>
      <Modal
        title="确认删除该 Skill？"
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
        <Text>删除后将无法恢复。若仍被 Agent 引用，删除会被拒绝。</Text>
      </Modal>
    </div>
  );
});

SkillEditorPage.displayName = 'SkillEditorPage';

export default SkillEditorPage;
