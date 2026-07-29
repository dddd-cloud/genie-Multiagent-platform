import React, { useMemo, useState } from 'react';
import { Button, Form, Input } from 'antd';
import { Navigate, useSearchParams } from 'react-router-dom';
import { MvpApiError } from '@/services/apiError';
import { useAuth } from '@/features/auth/useAuth';
import { resolveReturnTo } from '@/features/auth/returnTo';
import { Loading } from '@/components';

const LoginPage: React.FC = () => {
  const { status, login } = useAuth();
  const [searchParams] = useSearchParams();
  const [submitting, setSubmitting] = useState(false);
  const [credentialError, setCredentialError] = useState<string | null>(null);

  const returnTo = useMemo(
    () => resolveReturnTo(searchParams.get('returnTo')),
    [searchParams],
  );

  if (status === 'booting') {
    return <Loading loading className="h-full" />;
  }

  // Already authenticated OR just logged in → honor returnTo (defaults to /app).
  // Do NOT hardcode Navigate to="/app" or it clobbers /app/chat/:id after login.
  if (status === 'authenticated') {
    return <Navigate to={returnTo} replace />;
  }

  const onFinish = async (values: { username: string; password: string }) => {
    setCredentialError(null);
    setSubmitting(true);
    try {
      await login(values.username, values.password);
      // Re-render hits Navigate to={returnTo} above.
    } catch (error) {
      if (
        error instanceof MvpApiError &&
        error.code === 'AUTH_INVALID_CREDENTIALS'
      ) {
        setCredentialError(error.message || '用户名或密码错误');
        return;
      }
      setCredentialError(error instanceof Error ? error.message : '登录失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex h-full w-full items-center justify-center bg-page p-24">
      <div className="w-full max-w-[380px] rounded-lg border border-border bg-surface p-28 shadow-xs">
        <h1 className="mb-24 text-center text-[22px] font-semibold leading-[30px] text-text-primary">
          登录 JoyAgent
        </h1>
        <Form layout="vertical" onFinish={onFinish} requiredMark={false}>
          <Form.Item
            label="用户名"
            name="username"
            rules={[{
              required: true,
              message: '请输入用户名'
            }]}
          >
            <Input
              size="large"
              autoComplete="username"
              placeholder="username"
            />
          </Form.Item>
          <Form.Item
            label="密码"
            name="password"
            rules={[{
              required: true,
              message: '请输入密码'
            }]}
          >
            <Input.Password
              size="large"
              autoComplete="current-password"
              placeholder="password"
            />
          </Form.Item>
          {credentialError ? (
            <p className="mb-16 rounded-md bg-danger-soft px-12 py-8 text-[13px] text-danger">
              {credentialError}
            </p>
          ) : null}
          <Button
            type="primary"
            htmlType="submit"
            block
            size="large"
            loading={submitting}
          >
            登录
          </Button>
        </Form>
      </div>
    </div>
  );
};

LoginPage.displayName = 'LoginPage';

export default LoginPage;
