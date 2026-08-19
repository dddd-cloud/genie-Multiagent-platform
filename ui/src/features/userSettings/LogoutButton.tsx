import { memo, useCallback } from 'react';
import { Button } from 'antd';
import { useAuth } from '@/features/auth/useAuth';

const LogoutButton: GenieType.FC = memo(() => {
  const { logout } = useAuth();
  const handleLogout = useCallback(() => {
    void logout();
  }, [logout]);

  return (
    <Button size="small" onClick={handleLogout} data-testid="logout-button">
      退出
    </Button>
  );
});

LogoutButton.displayName = 'LogoutButton';

export default LogoutButton;
