import { memo } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import classNames from 'classnames';

const LINKS = [
  { to: '/app/admin/users', label: '用户管理' },
  { to: '/app/admin/usage', label: '用量统计' },
];

const AdminLayout: GenieType.FC = memo(() => {
  return (
    <div className="h-full overflow-auto bg-page">
      <div className="mx-auto flex max-w-[1180px] flex-col gap-20 px-24 py-28">
        <nav aria-label="管理员分类" data-testid="admin-nav">
          <ul className="m-0 flex list-none flex-wrap gap-8 p-0">
            {LINKS.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  className={({ isActive }) =>
                    classNames(
                      'inline-flex items-center rounded-[8px] px-14 py-7 text-[14px] leading-[22px] transition-colors',
                      isActive
                        ? 'bg-[#F0F0F2] font-medium text-text-primary'
                        : 'text-text-secondary hover:bg-[#F5F5F7]',
                    )
                  }
                >
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <Outlet />
      </div>
    </div>
  );
});

AdminLayout.displayName = 'AdminLayout';

export default AdminLayout;
