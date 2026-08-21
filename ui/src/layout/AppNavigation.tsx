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

type NavItem = {
  to: string;
  label: string;
  icon: ReactNode;
  adminOnly?: boolean;
};

const WORKBENCH_ITEMS: NavItem[] = [
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
];

const ADMIN_ITEMS: NavItem[] = [
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
];

function isActive(pathname: string, to: string): boolean {
  return pathname === to || pathname.startsWith(`${to}/`);
}

function NavList({ items }: { items: NavItem[] }) {
  const { pathname } = useLocation();
  const { user } = useAuth();

  return (
    <ul className="flex flex-col gap-1 m-0 p-0 list-none">
      {items
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
  );
}

const AppNavigation: GenieType.FC = memo(() => {
  return (
    <div className="w-full" data-testid="app-navigation">
      <NavList items={WORKBENCH_ITEMS} />
    </div>
  );
});

AppNavigation.displayName = 'AppNavigation';

export const AdminNavigation: GenieType.FC = memo(() => {
  const { user } = useAuth();
  if (user?.role !== 'ADMIN') {
    return null;
  }

  return (
    <nav
      className="w-full px-10 py-8 border-t border-border"
      data-testid="admin-navigation"
      aria-label="管理导航"
    >
      <NavList items={ADMIN_ITEMS} />
    </nav>
  );
});

AdminNavigation.displayName = 'AdminNavigation';

export default AppNavigation;
