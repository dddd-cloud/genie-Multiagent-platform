export type SettingsNavItem = {
  to: string;
  label: string;
  hint: string;
  /** Local memory and Phase2 resources only exist behind the Phase2 flag. */
  phase2Only?: boolean;
};

export const SETTINGS_NAV: SettingsNavItem[] = [
  {
    to: '/app/settings/models',
    label: '模型',
    hint: '添加、编辑可用模型和密钥',
  },
  {
    to: '/app/settings/memory',
    label: '本地记忆',
    hint: '只保存在这台电脑上的长期记忆',
    phase2Only: true,
  },
  {
    to: '/app/settings/preferences',
    label: '偏好',
    hint: '会话默认执行方式与模型',
  },
  {
    to: '/app/settings/account',
    label: '账户与用量',
    hint: '当前账户信息与我的调用量',
  },
];
