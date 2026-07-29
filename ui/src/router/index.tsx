import React, { Suspense } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import Layout from '@/layout/index';
import { Loading } from '@/components';
import AuthProvider from '@/features/auth/AuthProvider';
import RequireAuth from '@/features/auth/RequireAuth';

const Home = React.lazy(() => import('@/pages/Home'));
const LoginPage = React.lazy(() => import('@/pages/Login'));
const NotFound = React.lazy(() => import('@/components/NotFound'));
const ConversationLayout = React.lazy(
  () => import('@/features/conversation/ConversationLayout'),
);
const ConversationPage = React.lazy(
  () => import('@/features/conversation/ConversationPage'),
);

const router = createBrowserRouter([
  {
    element: (
      <AuthProvider>
        <Layout />
      </AuthProvider>
    ),
    children: [
      {
        index: true,
        element: <Navigate to="/app" replace />,
      },
      {
        path: 'login',
        element: (
          <Suspense fallback={<Loading loading className="h-full" />}>
            <LoginPage />
          </Suspense>
        ),
      },
      {
        element: <RequireAuth />,
        children: [
          {
            path: 'app',
            element: (
              <Suspense fallback={<Loading loading className="h-full" />}>
                <ConversationLayout />
              </Suspense>
            ),
            children: [
              {
                index: true,
                element: (
                  <Suspense fallback={<Loading loading className="h-full" />}>
                    <Home />
                  </Suspense>
                ),
              },
              {
                path: 'chat/:conversationId',
                element: (
                  <Suspense fallback={<Loading loading className="h-full" />}>
                    <ConversationPage />
                  </Suspense>
                ),
              },
            ],
          },
        ],
      },
      {
        path: '*',
        element: (
          <Suspense fallback={<Loading loading className="h-full" />}>
            <NotFound />
          </Suspense>
        ),
      },
    ],
  },
]);
export default router;
