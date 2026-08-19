import React, { Suspense } from 'react';
import { createBrowserRouter, Navigate, useParams } from 'react-router-dom';
import Layout from '@/layout/index';
import { Loading } from '@/components';
import AuthProvider from '@/features/auth/AuthProvider';
import RequireAuth from '@/features/auth/RequireAuth';
import { ChatSurfaceSlot } from '@/features/conversation/newConversationPath';
import UserSettingsProvider from '@/features/userSettings/UserSettingsProvider';

const LoginPage = React.lazy(() => import('@/pages/Login'));
const NotFound = React.lazy(() => import('@/components/NotFound'));
const ConversationLayout = React.lazy(
  () => import('@/features/conversation/ConversationLayout'),
);
const TeamListPage = React.lazy(
  () => import('@/features/phase2/teams/TeamListPage'),
);
const TeamEditorPage = React.lazy(
  () => import('@/features/phase2/teams/TeamEditorPage'),
);
const AdminGuard = React.lazy(() => import('@/features/admin/AdminGuard'));
const AdminLayout = React.lazy(() => import('@/features/admin/AdminLayout'));
const AdminUsersPage = React.lazy(
  () => import('@/features/admin/AdminUsersPage'),
);
const AdminUsagePage = React.lazy(
  () => import('@/features/admin/AdminUsagePage'),
);
const WorkspaceMount = React.lazy(
  () => import('@/layout/mounts/WorkspaceMount'),
);
const MarketplaceMount = React.lazy(
  () => import('@/layout/mounts/MarketplaceMount'),
);

function Page(element: React.ReactNode) {
  return <Suspense fallback={<Loading loading className="h-full" />}>{element}</Suspense>;
}

function RedirectTo({ to }: { to: string }) {
  const params = useParams();
  const path = to.replace(/:([A-Za-z]+)/g, (_, key: string) =>
    encodeURIComponent(String(params[key] ?? '')),
  );
  return <Navigate to={path} replace />;
}

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
        element: Page(<LoginPage />),
      },
      {
        element: <RequireAuth />,
        children: [
          {
            path: 'app',
            element: (
              <UserSettingsProvider>
                {Page(<ConversationLayout />)}
              </UserSettingsProvider>
            ),
            children: [
              {
                index: true,
                element: <ChatSurfaceSlot />,
              },
              {
                path: 'chat/:conversationId',
                element: <ChatSurfaceSlot />,
              },
              {
                path: 'workspace',
                element: Page(<WorkspaceMount />),
              },
              {
                path: 'marketplace',
                element: Page(<MarketplaceMount />),
              },
              {
                path: 'agents',
                element: <Navigate to="/app/settings/agents" replace />,
              },
              {
                path: 'agents/new',
                element: <Navigate to="/app/settings/agents/new" replace />,
              },
              {
                path: 'agents/:agentId',
                element: <RedirectTo to="/app/settings/agents/:agentId" />,
              },
              {
                path: 'teams',
                element: Page(<TeamListPage />),
              },
              {
                path: 'teams/new',
                element: Page(<TeamEditorPage />),
              },
              {
                path: 'teams/:teamId',
                element: Page(<TeamEditorPage />),
              },
              {
                path: 'skills',
                element: <Navigate to="/app/settings/skills" replace />,
              },
              {
                path: 'skills/new',
                element: <Navigate to="/app/settings/skills/new" replace />,
              },
              {
                path: 'skills/:skillId',
                element: <RedirectTo to="/app/settings/skills/:skillId" />,
              },
              {
                path: 'mcp',
                element: <Navigate to="/app/settings/mcp" replace />,
              },
              {
                path: 'mcp/new',
                element: <Navigate to="/app/settings/mcp/new" replace />,
              },
              {
                path: 'mcp/:serverId',
                element: <RedirectTo to="/app/settings/mcp/:serverId" />,
              },
              {
                path: 'settings/*',
                element: null,
              },
              {
                path: 'admin',
                element: Page(<AdminGuard />),
                children: [
                  {
                    element: Page(<AdminLayout />),
                    children: [
                      {
                        index: true,
                        element: <Navigate to="/app/admin/users" replace />,
                      },
                      {
                        path: 'users',
                        element: Page(<AdminUsersPage />),
                      },
                      {
                        path: 'usage',
                        element: Page(<AdminUsagePage />),
                      },
                    ],
                  },
                ],
              },
            ],
          },
        ],
      },
      {
        path: '*',
        element: Page(<NotFound />),
      },
    ],
  },
]);
export default router;
