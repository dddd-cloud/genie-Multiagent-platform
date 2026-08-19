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
const AgentListPage = React.lazy(
  () => import('@/features/phase2/agents/AgentListPage'),
);
const AgentEditorPage = React.lazy(
  () => import('@/features/phase2/agents/AgentEditorPage'),
);
const TeamListPage = React.lazy(
  () => import('@/features/phase2/teams/TeamListPage'),
);
const TeamEditorPage = React.lazy(
  () => import('@/features/phase2/teams/TeamEditorPage'),
);
const SkillListPage = React.lazy(
  () => import('@/features/phase2/skills/SkillListPage'),
);
const SkillEditorPage = React.lazy(
  () => import('@/features/phase2/skills/SkillEditorPage'),
);
const McpListPage = React.lazy(
  () => import('@/features/phase2/mcp/McpListPage'),
);
const McpEditorPage = React.lazy(
  () => import('@/features/phase2/mcp/McpEditorPage'),
);
const MemorySettingsPage = React.lazy(
  () => import('@/features/phase2/localMemory/MemorySettingsPage'),
);
const SettingsLayout = React.lazy(
  () => import('@/features/settings/SettingsLayout'),
);
const ModelSettingsPage = React.lazy(
  () => import('@/features/settings/pages/ModelSettingsPage'),
);
const PreferencesPage = React.lazy(
  () => import('@/features/settings/PreferencesPage'),
);
const AccountPage = React.lazy(() => import('@/features/settings/AccountPage'));
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
const GenerationMount = React.lazy(
  () => import('@/layout/mounts/GenerationMount'),
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
                path: 'generate',
                element: Page(<GenerationMount />),
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
                path: 'settings',
                element: Page(<SettingsLayout />),
                children: [
                  {
                    index: true,
                    element: <Navigate to="/app/settings/models" replace />,
                  },
                  {
                    path: 'models',
                    element: Page(<ModelSettingsPage />),
                  },
                  {
                    path: 'agents',
                    element: Page(<AgentListPage />),
                  },
                  {
                    path: 'agents/new',
                    element: Page(<AgentEditorPage />),
                  },
                  {
                    path: 'agents/:agentId',
                    element: Page(<AgentEditorPage />),
                  },
                  {
                    path: 'skills',
                    element: Page(<SkillListPage />),
                  },
                  {
                    path: 'skills/new',
                    element: Page(<SkillEditorPage />),
                  },
                  {
                    path: 'skills/:skillId',
                    element: Page(<SkillEditorPage />),
                  },
                  {
                    path: 'mcp',
                    element: Page(<McpListPage />),
                  },
                  {
                    path: 'mcp/new',
                    element: Page(<McpEditorPage />),
                  },
                  {
                    path: 'mcp/:serverId',
                    element: Page(<McpEditorPage />),
                  },
                  {
                    path: 'memory',
                    element: Page(<MemorySettingsPage />),
                  },
                  {
                    path: 'preferences',
                    element: Page(<PreferencesPage />),
                  },
                  {
                    path: 'profile',
                    element: <Navigate to="/app/settings/account" replace />,
                  },
                  {
                    path: 'account',
                    element: Page(<AccountPage />),
                  },
                ],
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
