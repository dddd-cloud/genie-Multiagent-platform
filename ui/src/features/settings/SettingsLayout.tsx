import { memo, useMemo, useState, type ReactNode } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { Input } from 'antd';
import {
  BookOutlined,
  CloseOutlined,
  ClusterOutlined,
  ControlOutlined,
  IdcardOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import classNames from 'classnames';
import { isPhase2Enabled } from '@/features/phase2/executionMode/featureFlag';
import { SETTINGS_NAV, type SettingsNavItem } from './settingsNav';
import { useSettingsModal } from './SettingsModalContext';

const SETTINGS_ICONS: Record<string, ReactNode> = {
  '/app/settings/models': <ClusterOutlined />,
  '/app/settings/memory': <BookOutlined />,
  '/app/settings/preferences': <ControlOutlined />,
  '/app/settings/account': <IdcardOutlined />,
};

function isNavActive(pathname: string, item: SettingsNavItem): boolean {
  return pathname === item.to || pathname.startsWith(`${item.to}/`);
}

const SettingsLayout: GenieType.FC = memo(() => {
  const { pathname } = useLocation();
  const { closeSettings } = useSettingsModal();
  const [query, setQuery] = useState('');
  const phase2 = isPhase2Enabled();
  const items = SETTINGS_NAV.filter((item) => phase2 || !item.phase2Only);
  const filtered = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    if (!keyword) {
      return items;
    }
    return items.filter(
      (item) =>
        item.label.toLowerCase().includes(keyword) ||
        item.hint.toLowerCase().includes(keyword),
    );
  }, [items, query]);
  const current = items.find((item) => isNavActive(pathname, item));

  return (
    <div className="flex h-[min(80vh,720px)] min-h-[480px] bg-surface">
      <aside className="flex w-[240px] shrink-0 flex-col border-r border-border bg-sidebar">
        <div className="flex items-center gap-8 px-12 pt-12 pb-8">
          <button
            type="button"
            aria-label="关闭设置"
            data-testid="settings-modal-close"
            onClick={closeSettings}
            className="flex h-28 w-28 items-center justify-center rounded-[8px] text-text-secondary hover:bg-[#F5F5F7] transition-colors"
          >
            <CloseOutlined className="text-[14px]" />
          </button>
        </div>
        <div className="px-12 pb-8">
          <Input
            allowClear
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            prefix={<SearchOutlined className="text-text-tertiary" />}
            placeholder="搜索设置"
            aria-label="搜索设置"
          />
        </div>
        <nav
          aria-label="设置分类"
          data-testid="settings-nav"
          className="min-h-0 flex-1 overflow-auto px-8 pb-12"
        >
          <ul className="m-0 flex list-none flex-col gap-1 p-0">
            {filtered.map((item) => {
              const active = isNavActive(pathname, item);
              return (
                <li key={item.to}>
                  <NavLink
                    to={item.to}
                    title={item.hint}
                    className={classNames(
                      'flex items-center gap-8 rounded-[8px] px-10 py-8 text-[14px] leading-[22px] transition-colors',
                      active
                        ? 'bg-[#F0F0F2] font-medium text-text-primary'
                        : 'text-text-primary hover:bg-[#F5F5F7]',
                    )}
                    aria-current={active ? 'page' : undefined}
                  >
                    <span className="text-[15px] text-text-secondary leading-none">
                      {SETTINGS_ICONS[item.to]}
                    </span>
                    <span>{item.label}</span>
                  </NavLink>
                </li>
              );
            })}
          </ul>
        </nav>
      </aside>
      <section className="min-w-0 flex-1 overflow-auto px-28 py-24">
        <h1 className="m-0 mb-20 text-[22px] font-semibold tracking-[-0.02em] text-text-primary">
          {current?.label ?? '设置'}
        </h1>
        <Outlet />
      </section>
    </div>
  );
});

SettingsLayout.displayName = 'SettingsLayout';

export default SettingsLayout;
