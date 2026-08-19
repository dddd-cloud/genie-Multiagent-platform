import type { ReactNode } from 'react';
import { memo } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import {
  AppstoreOutlined,
  BarChartOutlined,
  FolderOpenOutlined,
  UserOutlined,
} from '@ant-design/icons';
import classNames from 'classnames';
import { useAuth } from '@/features/auth/useAuth';
import Phase2Navigation from './Phase2Navigation';

type NavItem = {
  to: string;
  label: string;
  icon: ReactNode;
  adminOnly?: boolean;
};

type NavGroup = {
  key: string;
  title: string;
  items: NavItem[];
};

const WORKBENCH_GROUP: NavGroup = {
  key: 'workbench',
  title: '工作台',
  items: [
    {
      to: '/app/workspace',
      label: '工作区',
      icon: <FolderOpenOutlined />,
    },
    {
      to: '/app/marketplace',
      label: '资源广场',
      icon: <AppstoreOutlined />,
    },
  ],
};

const ADMIN_GROUP: NavGroup = {
  key: 'admin',
  title: '管理',
  items: [
    {
      to: '/app/admin/users',
      label: '用户管理',
      icon: <UserOutlined />,
      adminOnly: true,
    },
    {
      to: '/app/admin/usage',
      label: '用量',
      icon: <BarChartOutlined />,
      adminOnly: true,
    },
  ],
};

function isActive(pathname: string, to: string): boolean {
  return pathname === to || pathname.startsWith(`${to}/`);
}

const AppNavigation: GenieType.FC = memo(() => {
  const { pathname } = useLocation();
  const { user } = useAuth();
  const groups =
    user?.role === 'ADMIN' ? [WORKBENCH_GROUP, ADMIN_GROUP] : [WORKBENCH_GROUP];

  return (
    <div className="w-full">
      <Phase2Navigation />
      <nav
        className="w-full px-10 py-8 border-b border-border"
        data-testid="app-navigation"
        aria-label="工作台与管理导航"
      >
        {groups.map((group) => (
          <div key={group.key} className="mb-4 last:mb-0">
            <div className="px-10 pb-2 pt-4 text-[12px] leading-[18px] text-text-tertiary">
              {group.title}
            </div>
            <ul className="flex flex-col gap-1 m-0 p-0 list-none">
              {group.items
                .filter((item) => !item.adminOnly || user?.role === 'ADMIN')
                .map((item) => {
                  const active = isActive(pathname, item.to);
                  return (
                    <li key={item.to}>
                      <NavLink
                        to={item.to}
                        className={classNames(
                          'flex items-center gap-8 rounded-[8px] px-10 py-7 text-[14px] leading-[22px] transition-colors',
                          active
                            ? 'bg-[#F0F0F2] text-text-primary font-medium'
                            : 'text-text-primary hover:bg-[#F5F5F7]',
                        )}
                        aria-current={active ? 'page' : undefined}
                      >
                        <span className="text-[15px] text-text-secondary leading-none">
                          {item.icon}
                        </span>
                        <span>{item.label}</span>
                      </NavLink>
                    </li>
                  );
                })}
            </ul>
          </div>
        ))}
      </nav>
    </div>
  );
});

AppNavigation.displayName = 'AppNavigation';

export default AppNavigation;
