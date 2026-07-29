import React from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Loading } from '@/components';
import { loginPathWithReturnTo } from './returnTo';
import { useAuth } from './useAuth';

const RequireAuth: React.FC = () => {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'booting') {
    return <Loading loading className="h-full" />;
  }

  if (status === 'unauthenticated') {
    return (
      <Navigate
        to={loginPathWithReturnTo(location.pathname, location.search)}
        replace
      />
    );
  }

  return <Outlet />;
};

RequireAuth.displayName = 'RequireAuth';

export default RequireAuth;
