import { memo, useCallback, useState } from 'react';
import { Dropdown } from 'antd';
import { UpOutlined } from '@ant-design/icons';
import classNames from 'classnames';
import { useAuth } from '@/features/auth/useAuth';
import { useSettingsModal } from '@/features/settings/SettingsModalContext';

const SidebarUserMenu: GenieType.FC = memo(() => {
  const { user, logout } = useAuth();
  const { openSettings } = useSettingsModal();
  const [open, setOpen] = useState(false);
  const name = user?.displayName || user?.username || '—';

  const handleOpenSettings = useCallback(() => {
    setOpen(false);
    openSettings();
  }, [openSettings]);

  const handleLogout = useCallback(() => {
    setOpen(false);
    void logout();
  }, [logout]);

  return (
    <Dropdown
      trigger={['click']}
      placement="top"
      open={open}
      onOpenChange={setOpen}
      menu={{
        items: [
          {
            key: 'settings',
            label: <span data-testid="settings-menu-item">设置</span>,
            onClick: handleOpenSettings,
          },
          {
            type: 'divider',
          },
          {
            key: 'logout',
            label: <span data-testid="logout-button">退出登录</span>,
            onClick: handleLogout,
          },
        ],
      }}
    >
      <button
        type="button"
        data-testid="sidebar-user-menu"
        aria-expanded={open}
        aria-haspopup="menu"
        className="w-full px-14 py-12 border-t border-border flex items-center justify-between gap-8 text-left hover:bg-[#F5F5F7] transition-colors duration-150"
      >
        <div className="min-w-0" data-testid="user-profile-compact">
          <div className="text-[12px] text-text-secondary leading-[18px]">
            当前用户
          </div>
          <div className="text-[14px] font-medium text-text-primary truncate leading-[22px]">
            {name}
          </div>
        </div>
        <UpOutlined
          className={classNames(
            'shrink-0 text-[12px] text-text-secondary transition-transform duration-150',
            open && 'rotate-180',
          )}
        />
      </button>
    </Dropdown>
  );
});

SidebarUserMenu.displayName = 'SidebarUserMenu';

export default SidebarUserMenu;
