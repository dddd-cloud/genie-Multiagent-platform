import { memo, useCallback, useState } from 'react';
import { Alert, Button, Select, Spin, Switch, message } from 'antd';
import type { UserPreferences, UserPreferencesPatch } from '@/contracts';
import { EXECUTION_MODE_PREFERENCES, OUTPUT_STYLES } from '@/contracts';
import { useUserSettings } from '@/features/userSettings/useUserSettings';
import { MvpApiError } from '@/services/apiError';
import SettingRow from './SettingRow';

const EXECUTION_MODE_LABELS: Record<
  (typeof EXECUTION_MODE_PREFERENCES)[number],
  string
> = {
  AUTO: '自动选择',
  DIRECT: '直接由单个 Agent 回答',
  ORCHESTRATED: '编排多个 Agent 协作',
};

const OUTPUT_STYLE_LABELS: Record<(typeof OUTPUT_STYLES)[number], string> = {
  dataAgent: '智能问数',
  html: '网页模式',
  docs: '文档模式',
  ppt: 'PPT 模式',
  table: '表格模式',
};

const FOLLOW_SYSTEM = '__follow_system__';

const PreferencesPage: GenieType.FC = memo(() => {
  const { preferences, status, error, reload, save } = useUserSettings();
  const [savingKey, setSavingKey] = useState<keyof UserPreferences | null>(null);

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
              label="默认开启深度思考"
              hint="更慢但更细致，适合复杂任务。"
            >
              <Switch
                checked={preferences.defaultDeepThink}
                loading={savingKey === 'defaultDeepThink'}
                onChange={(checked) => void apply('defaultDeepThink', checked)}
              />
            </SettingRow>
            <SettingRow
              label="默认输出风格"
              hint="决定新会话预选哪种输出模式。"
              last
            >
              <Select
                className="w-[260px]"
                value={preferences.defaultOutputStyle || FOLLOW_SYSTEM}
                loading={savingKey === 'defaultOutputStyle'}
                options={[
                  {
                    value: FOLLOW_SYSTEM,
                    label: '跟随系统默认',
                  },
                  ...OUTPUT_STYLES.map((style) => ({
                    value: style,
                    label: OUTPUT_STYLE_LABELS[style],
                  })),
                ]}
                onChange={(value) =>
                  void apply(
                    'defaultOutputStyle',
                    value === FOLLOW_SYSTEM ? '' : value,
                  )
                }
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
