import { memo, useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Modal, Space, Spin, Typography, message } from 'antd';
import type { Phase2AgentResponse } from '@/contracts/phase2';
import { listAgents } from '@/services/phase2/agents';
import {
  createTeam,
  deleteTeam,
  getTeam,
  updateTeam,
} from '@/services/phase2/teams';
import VersionConflictAlert from '../VersionConflictAlert';
import { isVersionConflict, phase2ErrorMessage } from '../phase2UiError';
import TeamForm from './TeamForm';
import {
  emptyTeamFormState,
  formStateToCreateRequest,
  formStateToUpdateRequest,
  teamToFormState,
  validateTeamForm,
  type TeamFormState,
} from './teamFormModel';

const { Title, Text } = Typography;

const TeamEditorPage: GenieType.FC = memo(() => {
  const { teamId } = useParams<{ teamId?: string }>();
  const isNew = !teamId || teamId === 'new';
  const navigate = useNavigate();

  const [form, setForm] = useState<TeamFormState>(emptyTeamFormState());
  const [agents, setAgents] = useState<Phase2AgentResponse[]>([]);
  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [versionConflict, setVersionConflict] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);

  const loadTeam = useCallback(
    async (signal?: AbortSignal) => {
      if (isNew || !teamId) {
        setForm(emptyTeamFormState());
        setVersionConflict(false);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const team = await getTeam(teamId, signal);
        if (!team) {
          setError('团队不存在');
          return;
        }
        setForm(teamToFormState(team));
        setVersionConflict(false);
      } catch (err: unknown) {
        setError(phase2ErrorMessage(err));
      } finally {
        setLoading(false);
      }
    },
    [isNew, teamId],
  );

  useEffect(() => {
    const controller = new AbortController();
    void (async () => {
      try {
        const agentList = await listAgents(controller.signal);
        setAgents(agentList ?? []);
        await loadTeam(controller.signal);
      } catch (err: unknown) {
        if (!controller.signal.aborted) {
          setError(phase2ErrorMessage(err));
          setLoading(false);
        }
      }
    })();
    return () => controller.abort();
  }, [loadTeam]);

  const handleSave = async () => {
    const validation = validateTeamForm(form);
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
        const created = await createTeam(formStateToCreateRequest(form));
        if (!created) {
          message.error('创建失败');
          return;
        }
        message.success('已创建');
        navigate(`/app/teams/${created.id}`, { replace: true });
        return;
      }
      if (!teamId) return;
      const updated = await updateTeam(teamId, formStateToUpdateRequest(form));
      if (!updated) {
        message.error('保存失败');
        return;
      }
      setForm(teamToFormState(updated));
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

  const handleDelete = () => {
    if (versionConflict) {
      message.warning('请先重新加载服务器版本');
      return;
    }
    if (!teamId || form.version == null) {
      setError('团队详情尚未加载完成，请稍候重试');
      return;
    }
    setDeleteOpen(true);
  };

  const confirmDelete = async () => {
    if (!teamId || form.version == null) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await deleteTeam(teamId, { version: form.version });
      setDeleteOpen(false);
      message.success('已删除');
      navigate('/app/teams');
    } catch (err: unknown) {
      setDeleteOpen(false);
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

  return (
    <div className="h-full w-full overflow-auto p-24" data-testid="team-editor-page">
      <div className="flex items-center justify-between gap-12 mb-16">
        <div>
          <Title level={4} className="!mb-4">
            {isNew ? '新建团队' : '编辑团队'}
          </Title>
          <Text type="secondary">
            {isNew ? '保存后即可在对话框的 Ensemble 中选用' : `ID: ${teamId}`}
          </Text>
        </div>
        <Space wrap>
          <Button onClick={() => navigate('/app/teams')}>返回列表</Button>
          <Button
            type="primary"
            loading={saving}
            disabled={versionConflict}
            onClick={() => void handleSave()}
            data-testid="team-save"
          >
            保存
          </Button>
          {!isNew ? (
            <Button
              danger
              disabled={versionConflict || saving}
              onClick={handleDelete}
              data-testid="team-delete"
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
            onReload={() => void loadTeam()}
          />
        </div>
      ) : null}

      {error && !versionConflict ? (
        <Alert type="error" showIcon className="mb-16" message={error} />
      ) : null}

      <Spin spinning={loading}>
        <div className="max-w-[720px]">
          <TeamForm
            value={form}
            onChange={setForm}
            agents={agents}
            disabled={saving}
            readOnly={versionConflict}
          />
        </div>
      </Spin>

      <Modal
        title="确认删除该团队？"
        open={deleteOpen}
        okText="删除"
        cancelText="取消"
        okType="danger"
        confirmLoading={saving}
        maskClosable={!saving}
        closable={!saving}
        onOk={() => void confirmDelete()}
        onCancel={() => {
          if (!saving) {
            setDeleteOpen(false);
          }
        }}
      >
        <Text>删除团队不会影响其中的 Agent，此操作不可恢复。</Text>
      </Modal>
    </div>
  );
});

TeamEditorPage.displayName = 'TeamEditorPage';

export default TeamEditorPage;
