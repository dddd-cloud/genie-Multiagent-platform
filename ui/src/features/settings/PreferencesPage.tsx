import { memo, useCallback, useEffect, useState } from 'react';
import { Alert, Button, Select, Spin, Switch, message } from 'antd';
import type { UserPreferences, UserPreferencesPatch } from '@/contracts';
import { EXECUTION_MODE_PREFERENCES } from '@/contracts';
import type { Phase2ModelResponse } from '@/contracts/phase2';
import { useUserSettings } from '@/features/userSettings/useUserSettings';
import { MvpApiError } from '@/services/apiError';
import { listModels } from '@/services/phase2/models';
import SettingRow from './SettingRow';

const EXECUTION_MODE_LABELS: Record<
  (typeof EXECUTION_MODE_PREFERENCES)[number],
  string
> = {
  AUTO: '自动选择',
  DIRECT: '直接由单个 Agent 回答',
  ORCHESTRATED: '编排多个 Agent 协作',
};

const PreferencesPage: GenieType.FC = memo(() => {
  const { preferences, status, error, reload, save } = useUserSettings();
  const [savingKey, setSavingKey] = useState<keyof UserPreferences | null>(null);
  const [models, setModels] = useState<Phase2ModelResponse[]>([]);

  const apply = useCallback(
    async <K extends keyof UserPreferences>(
      key: K,
      value: UserPreferences[K],
    ) => {
      if (preferences[key] === value) {
        return;
      }
      setSavingKey(key);
      try {
        await save({ [key]: value } as UserPreferencesPatch);
        message.success('已保存');
      } catch (err: unknown) {
        message.error(
          err instanceof MvpApiError ? err.message : '保存失败，请重试',
        );
      } finally {
        setSavingKey(null);
      }
    },
    [preferences, save],
  );

  useEffect(() => {
    const controller = new AbortController();
    listModels(controller.signal)
      .then((items) => {
        setModels(
          (items ?? []).filter((item) => item.name !== 'system-default'),
        );
      })
      .catch(() => {
        setModels([]);
      });
    return () => controller.abort();
  }, []);

  if (status === 'error') {
    return (
      <Alert
        type="warning"
        showIcon
        message="读取设置失败"
        description={error ?? '暂时无法读取你的偏好，下面显示的是默认值。'}
        action={
          <Button size="small" onClick={() => void reload()}>
            重试
          </Button>
        }
      />
    );
  }

  return (
    <Spin spinning={status === 'loading'}>
      <div
        className="flex flex-col gap-28"
        data-testid="settings-preferences"
      >
        <section>
          <h2 className="m-0 mb-8 px-4 text-[13px] font-medium tracking-[0.02em] text-text-tertiary">
            新会话默认值
          </h2>
          <p className="m-0 mb-8 px-4 text-[12px] leading-[18px] text-text-tertiary">
            这些默认值用于开启新会话时预设输入框，发送前仍可临时改；已有会话沿用它上一轮的选择。
          </p>
          <div className="overflow-hidden rounded-xl bg-surface shadow-xs">
            <SettingRow
              label="默认执行方式"
              hint="新会话默认走哪条执行路径。"
            >
              <Select
                className="w-[260px]"
                value={preferences.defaultExecutionMode}
                loading={savingKey === 'defaultExecutionMode'}
                options={EXECUTION_MODE_PREFERENCES.map((mode) => ({
                  value: mode,
                  label: EXECUTION_MODE_LABELS[mode],
                }))}
                onChange={(value) => void apply('defaultExecutionMode', value)}
              />
            </SettingRow>
            <SettingRow
              label="默认模型"
              hint="新对话和所有 Agent 都会使用这个模型，也可在输入框左侧随时切换。"
              last
            >
              <Select
                className="w-[260px]"
                value={preferences.preferredModelName || undefined}
                loading={savingKey === 'preferredModelName'}
                placeholder={models.length === 0 ? '请先在「模型」中配置' : '选择默认模型'}
                options={models.map((item) => ({
                  value: item.name,
                  label: item.displayName || item.name,
                }))}
                onChange={(value) => void apply('preferredModelName', value)}
                data-testid="preferences-default-model"
              />
            </SettingRow>
          </div>
        </section>

        <section>
          <h2 className="m-0 mb-8 px-4 text-[13px] font-medium tracking-[0.02em] text-text-tertiary">
            界面
          </h2>
          <div className="overflow-hidden rounded-xl bg-surface shadow-xs">
            <SettingRow
              label="默认收起侧边栏"
              hint="小屏幕上能多留出正文空间，登录后仍可随时展开。"
              last
            >
              <Switch
                checked={preferences.sidebarCollapsed}
                loading={savingKey === 'sidebarCollapsed'}
                onChange={(checked) => void apply('sidebarCollapsed', checked)}
              />
            </SettingRow>
          </div>
        </section>
      </div>
    </Spin>
  );
});

PreferencesPage.displayName = 'PreferencesPage';

export default PreferencesPage;
