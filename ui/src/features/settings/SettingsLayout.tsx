import { memo } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import classNames from 'classnames';
import { isPhase2Enabled } from '@/features/phase2/executionMode/featureFlag';
import { SETTINGS_NAV } from './settingsNav';

const SettingsLayout: GenieType.FC = memo(() => {
  const phase2 = isPhase2Enabled();
  const items = SETTINGS_NAV.filter((item) => phase2 || !item.phase2Only);

  return (
    <div className="h-full overflow-auto bg-page">
      <div className="mx-auto flex max-w-[1180px] flex-col gap-24 px-24 py-36">
        <header>
          <h1 className="m-0 text-[28px] font-semibold tracking-[-0.02em] text-text-primary">
            设置
          </h1>
          <p className="mt-8 mb-0 text-[15px] leading-[22px] text-text-secondary">
            这里的偏好会跟着你的账户走，换一台电脑登录也还在。
          </p>
        </header>

        <nav aria-label="设置分类" data-testid="settings-nav">
          <ul className="m-0 flex list-none flex-wrap gap-8 p-0">
            {items.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  end={
                    item.to === '/app/settings/models' ||
                    item.to === '/app/settings/preferences' ||
                    item.to === '/app/settings/account' ||
                    item.to === '/app/settings/memory'
                  }
                  title={item.hint}
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

SettingsLayout.displayName = 'SettingsLayout';

export default SettingsLayout;
