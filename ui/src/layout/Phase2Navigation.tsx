import type { ReactNode } from 'react';
import { memo } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import {
  ApiOutlined,
  CommentOutlined,
  DatabaseOutlined,
  RobotOutlined,
  TeamOutlined,
  ToolOutlined,
} from '@ant-design/icons';
import classNames from 'classnames';
import { isPhase2Enabled } from '@/features/phase2/executionMode/featureFlag';

const LINKS: Array<{
  to: string;
  label: string;
  match: 'session' | 'prefix' | 'exact';
  icon: ReactNode;
}> = [
  {
    to: '/app',
    label: '会话',
    match: 'session',
    icon: <CommentOutlined />,
  },
  {
    to: '/app/agents',
    label: 'Agent',
    match: 'prefix',
    icon: <RobotOutlined />,
  },
  {
    to: '/app/teams',
    label: '团队',
    match: 'prefix',
    icon: <TeamOutlined />,
  },
  {
    to: '/app/skills',
    label: 'Skill',
    match: 'prefix',
    icon: <ToolOutlined />,
  },
  {
    to: '/app/mcp',
    label: 'MCP',
    match: 'prefix',
    icon: <ApiOutlined />,
  },
  {
    to: '/app/settings/memory',
    label: '本地记忆',
    match: 'exact',
    icon: <DatabaseOutlined />,
  },
];

function isLinkActive(
  pathname: string,
  link: (typeof LINKS)[number],
): boolean {
  if (link.match === 'session') {
    return pathname === '/app' || pathname.startsWith('/app/chat');
  }
  if (link.match === 'exact') {
    return pathname === link.to;
  }
  return pathname === link.to || pathname.startsWith(`${link.to}/`);
}

const Phase2Navigation: GenieType.FC = memo(() => {
  const { pathname } = useLocation();

  if (!isPhase2Enabled()) {
    return null;
  }

  return (
    <nav
      className="w-full px-10 py-8 border-b border-border"
      data-testid="phase2-navigation"
      aria-label="功能导航"
    >
      <ul className="flex flex-col gap-1 m-0 p-0 list-none">
        {LINKS.map((link) => {
          const active = isLinkActive(pathname, link);
          return (
            <li key={link.to}>
              <NavLink
                to={link.to}
                end={link.match !== 'prefix'}
                className={classNames(
                  'flex items-center gap-8 rounded-[8px] px-10 py-7 text-[14px] leading-[22px] transition-colors',
                  active
                    ? 'bg-[#F0F0F2] text-text-primary font-medium'
                    : 'text-text-primary hover:bg-[#F5F5F7]',
                )}
                aria-current={active ? 'page' : undefined}
              >
                <span className="text-[15px] text-text-secondary leading-none">
                  {link.icon}
                </span>
                <span>{link.label}</span>
              </NavLink>
            </li>
          );
        })}
      </ul>
    </nav>
  );
});

Phase2Navigation.displayName = 'Phase2Navigation';

export default Phase2Navigation;
