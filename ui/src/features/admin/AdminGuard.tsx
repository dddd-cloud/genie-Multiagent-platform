import { memo } from 'react';
import { Link, Outlet } from 'react-router-dom';
import { Loading } from '@/components';
import { useAuth } from '@/features/auth/useAuth';

/**
 * Client-side gate for admin screens. The server is still the authority (`/api/v1/admin/**` requires
 * ROLE_ADMIN); this only avoids showing a non-admin a screen whose every request would fail.
 */
const AdminGuard: GenieType.FC = memo(() => {
  const { status, user } = useAuth();

  if (status === 'booting') {
    return <Loading loading className="h-full" />;
  }

  if (user?.role !== 'ADMIN') {
    return (
      <div className="flex h-full w-full items-center justify-center bg-page px-24">
        <div
          className="max-w-[420px] rounded-xl bg-surface px-24 py-28 text-center shadow-xs"
          data-testid="admin-forbidden"
        >
          <p className="m-0 text-[16px] font-medium text-text-primary">
            这里只对管理员开放
          </p>
          <p className="mt-8 mb-16 text-[14px] leading-[22px] text-text-secondary">
            如果你认为应该有权限，请联系管理员为你的账户开通。
          </p>
          <Link
            to="/app"
            className="text-[14px] text-brand hover:text-brand-hover"
          >
            返回会话
          </Link>
        </div>
      </div>
    );
  }

  return <Outlet />;
});

AdminGuard.displayName = 'AdminGuard';

export default AdminGuard;
