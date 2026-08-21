import { memo, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Input, Modal, Spin, message } from 'antd';
import type { Phase2ModelResponse } from '@/contracts/phase2';
import { phase2ErrorMessage } from '@/features/phase2/phase2UiError';
import {
  createModel,
  deleteModel,
  getModel,
  updateModel,
} from '@/services/phase2/models';
import SettingRow from '../SettingRow';

type FormState = {
  name: string;
  displayName: string;
  model: string;
  baseUrl: string;
  interfaceUrl: string;
  maxTokens: string;
  temperature: string;
  maxInputTokens: string;
  apiKey: string;
};

const EMPTY: FormState = {
  name: '',
  displayName: '',
  model: '',
  baseUrl: '',
  interfaceUrl: '/v1/chat/completions',
  maxTokens: '16384',
  temperature: '0',
  maxInputTokens: '100000',
  apiKey: '',
};

function fromResponse(item: Phase2ModelResponse): FormState {
  return {
    name: item.name ?? '',
    displayName: item.displayName ?? item.name ?? '',
    model: item.model ?? '',
    baseUrl: item.baseUrl ?? '',
    interfaceUrl: item.interfaceUrl || '/v1/chat/completions',
    maxTokens: String(item.maxTokens ?? 16384),
    temperature: String(item.temperature ?? 0),
    maxInputTokens: String(item.maxInputTokens ?? 100000),
    apiKey: '',
  };
}

const ModelEditorPage: GenieType.FC = memo(() => {
  const { modelId } = useParams<{ modelId?: string }>();
  const isNew = !modelId || modelId === 'new';
  const navigate = useNavigate();
  const [form, setForm] = useState<FormState>(EMPTY);
  const [loaded, setLoaded] = useState<Phase2ModelResponse | null>(null);
  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [deleteOpen, setDeleteOpen] = useState(false);

  useEffect(() => {
    if (isNew || !modelId) {
      setForm(EMPTY);
      setLoaded(null);
      setLoading(false);
      return;
    }
    const controller = new AbortController();
    setLoading(true);
    getModel(modelId, controller.signal)
      .then((item) => {
        if (!item) {
          setError('模型不存在');
          return;
        }
        setLoaded(item);
        setForm(fromResponse(item));
        setError(null);
      })
      .catch((err: unknown) => {
        if (!controller.signal.aborted) {
          setError(phase2ErrorMessage(err));
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      });
    return () => controller.abort();
  }, [isNew, modelId]);

  const patch = (partial: Partial<FormState>) => {
    setForm((prev) => ({
      ...prev,
      ...partial,
    }));
  };

  const handleSave = async () => {
    if (!form.name.trim() || !form.model.trim()) {
      message.error('请填写标识和模型名');
      return;
    }
    if (isNew && !form.apiKey.trim()) {
      message.error('新建模型需要填写 API Key');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const body = {
        name: form.name.trim(),
        displayName: form.displayName.trim() || form.name.trim(),
        model: form.model.trim(),
        baseUrl: form.baseUrl.trim(),
        interfaceUrl: form.interfaceUrl.trim() || '/v1/chat/completions',
        maxTokens: Number(form.maxTokens) || 16384,
        temperature: Number(form.temperature) || 0,
        maxInputTokens: Number(form.maxInputTokens) || 100000,
        ...(form.apiKey.trim() ? { apiKey: form.apiKey.trim() } : {}),
      };
      const saved = isNew
        ? await createModel(body)
        : await updateModel(modelId as string, body);
      message.success('已保存');
      if (isNew && saved?.id) {
        navigate(`/app/settings/models/${encodeURIComponent(saved.id)}`, {
          replace: true,
        });
      } else if (saved) {
        setLoaded(saved);
        setForm(fromResponse(saved));
      }
    } catch (err: unknown) {
      setError(phase2ErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!modelId || isNew) {
      return;
    }
    setSaving(true);
    try {
      await deleteModel(modelId);
      message.success('已删除');
      navigate('/app/settings/models', { replace: true });
    } catch (err: unknown) {
      setError(phase2ErrorMessage(err));
      setSaving(false);
    }
  };

  return (
    <div data-testid="settings-model-editor">
      <button
        type="button"
        className="mb-12 border-0 bg-transparent p-0 text-[13px] text-text-secondary hover:text-text-primary"
        onClick={() => navigate('/app/settings/models')}
      >
        ‹ 模型
      </button>
      <h2 className="m-0 mb-16 text-[20px] font-semibold tracking-[-0.02em] text-text-primary">
        {isNew ? '新建模型' : form.displayName || form.name || '模型'}
      </h2>
      {error ? (
        <Alert type="error" showIcon className="mb-16" message={error} />
      ) : null}
      <Spin spinning={loading}>
        <div className="overflow-hidden rounded-xl bg-surface shadow-xs">
          <SettingRow label="名称" hint="显示在选择器和列表里的名字。">
            <Input
              className="w-[260px]"
              value={form.displayName}
              onChange={(e) => patch({ displayName: e.target.value })}
              data-testid="model-display-name"
            />
          </SettingRow>
          <SettingRow label="标识" hint="英文、数字、点、下划线和短横线。">
            <Input
              className="w-[260px]"
              value={form.name}
              disabled={!isNew}
              onChange={(e) => patch({ name: e.target.value })}
              data-testid="model-name"
            />
          </SettingRow>
          <SettingRow label="模型名" hint="调用上游接口时使用的 model。">
            <Input
              className="w-[260px]"
              value={form.model}
              onChange={(e) => patch({ model: e.target.value })}
              data-testid="model-id"
            />
          </SettingRow>
          <SettingRow label="Base URL">
            <Input
              className="w-[260px]"
              value={form.baseUrl}
              onChange={(e) => patch({ baseUrl: e.target.value })}
              placeholder="https://"
              data-testid="model-base-url"
            />
          </SettingRow>
          <SettingRow label="接口路径">
            <Input
              className="w-[260px]"
              value={form.interfaceUrl}
              onChange={(e) => patch({ interfaceUrl: e.target.value })}
              data-testid="model-interface-url"
            />
          </SettingRow>
          <SettingRow label="Max tokens">
            <Input
              className="w-[260px]"
              value={form.maxTokens}
              onChange={(e) => patch({ maxTokens: e.target.value })}
              data-testid="model-max-tokens"
            />
          </SettingRow>
          <SettingRow label="Temperature">
            <Input
              className="w-[260px]"
              value={form.temperature}
              onChange={(e) => patch({ temperature: e.target.value })}
              data-testid="model-temperature"
            />
          </SettingRow>
          <SettingRow label="Max input tokens" last>
            <Input
              className="w-[260px]"
              value={form.maxInputTokens}
              onChange={(e) => patch({ maxInputTokens: e.target.value })}
              data-testid="model-max-input-tokens"
            />
          </SettingRow>
        </div>

        <h2 className="m-0 mb-8 mt-28 px-4 text-[13px] font-medium tracking-[0.02em] text-text-tertiary">
          API Key
        </h2>
        <div className="overflow-hidden rounded-xl bg-surface shadow-xs">
          <SettingRow
            label="密钥"
            hint={
              loaded?.apiKeyConfigured
                ? '已配置。留空保存表示不修改；输入新值会替换旧密钥。'
                : '保存后密钥不会回传到浏览器。'
            }
            last
          >
            <Input.Password
              className="w-[260px]"
              value={form.apiKey}
              visibilityToggle={false}
              autoComplete="new-password"
              placeholder={
                loaded?.apiKeyConfigured ? '••••••••' : '输入 API Key'
              }
              onChange={(e) => patch({ apiKey: e.target.value })}
              data-testid="model-api-key"
            />
          </SettingRow>
        </div>

        <div className="mt-20 flex items-center justify-between">
          {!isNew ? (
            <Button
              danger
              disabled={saving}
              onClick={() => setDeleteOpen(true)}
              data-testid="model-delete"
            >
              删除
            </Button>
          ) : (
            <span />
          )}
          <Button
            type="primary"
            className="rounded-full"
            loading={saving}
            onClick={() => void handleSave()}
            data-testid="model-save"
          >
            保存
          </Button>
        </div>
      </Spin>

      <Modal
        title="删除这个模型？"
        open={deleteOpen}
        okText="删除"
        cancelText="取消"
        okType="danger"
        confirmLoading={saving}
        onOk={() => void handleDelete()}
        onCancel={() => setDeleteOpen(false)}
      >
        删除后对话将无法再选择它。此操作不可恢复。
      </Modal>
    </div>
  );
});

ModelEditorPage.displayName = 'ModelEditorPage';

export default ModelEditorPage;
