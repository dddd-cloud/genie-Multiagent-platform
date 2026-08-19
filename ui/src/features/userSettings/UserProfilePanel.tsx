import { memo } from 'react';
import { useAuth } from '@/features/auth/useAuth';

const ROLE_LABELS: Record<string, string> = {
  ADMIN: '管理员',
  USER: '普通用户',
};

export interface UserProfilePanelProps {
  compact?: boolean;
}

const UserProfilePanel: GenieType.FC<UserProfilePanelProps> = memo(({ compact }) => {
  const { user } = useAuth();
  const name = user?.displayName || user?.username || '—';

  if (compact) {
    return (
      <div className="min-w-0" data-testid="user-profile-compact">
        <div className="text-[12px] text-text-secondary leading-[18px]">当前用户</div>
        <div className="text-[14px] font-medium text-text-primary truncate leading-[22px]">
          {name}
        </div>
      </div>
    );
  }

  return (
    <div data-testid="user-profile-panel">
      <div className="text-[13px] text-text-tertiary">用户名</div>
      <div className="mt-2 text-[15px] text-text-primary">{user?.username ?? '—'}</div>
      <div className="mt-12 text-[13px] text-text-tertiary">显示名</div>
      <div className="mt-2 text-[15px] text-text-primary">{user?.displayName || '—'}</div>
      <div className="mt-12 text-[13px] text-text-tertiary">角色</div>
      <div className="mt-2 text-[15px] text-text-primary">
        {user ? ROLE_LABELS[user.role] ?? user.role : '—'}
      </div>
    </div>
  );
});

UserProfilePanel.displayName = 'UserProfilePanel';

export default UserProfilePanel;
