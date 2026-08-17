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
              {
                path: 'agents',
                element: (
                  <Suspense fallback={<Loading loading className="h-full" />}>
                    <AgentListPage />
                  </Suspense>
                ),
              },
              {
                path: 'agents/new',
                element: (
                  <Suspense fallback={<Loading loading className="h-full" />}>
                    <AgentEditorPage />
                  </Suspense>
                ),
              },
              {
                path: 'agents/:agentId',
                element: (
                  <Suspense fallback={<Loading loading className="h-full" />}>
                    <AgentEditorPage />
                  </Suspense>
                ),
              },
              {
                path: 'teams',
                element: (
                  <Suspense fallback={<Loading loading className="h-full" />}>
                    <TeamListPage />
                  </Suspense>
                ),
              },
              {
                path: 'teams/new',
                element: (
                  <Suspense fallback={<Loading loading className="h-full" />}>
                    <TeamEditorPage />
                  </Suspense>
                ),
              },
              {
                path: 'teams/:teamId',
                element: (
                  <Suspense fallback={<Loading loading className="h-full" />}>
                    <TeamEditorPage />
                  </Suspense>
                ),
              },
              {
                path: 'skills',
                element: (
                  <Suspense fallback={<Loading loading className="h-full" />}>
                    <SkillListPage />
                  </Suspense>
                ),
              },
              {
                path: 'skills/new',
                element: (
                  <Suspense fallback={<Loading loading className="h-full" />}>
                    <SkillEditorPage />
                  </Suspense>
                ),
              },
              {
                path: 'skills/:skillId',
                element: (
                  <Suspense fallback={<Loading loading className="h-full" />}>
                    <SkillEditorPage />
                  </Suspense>
                ),
              },
              {
                path: 'mcp',
                element: (
                  <Suspense fallback={<Loading loading className="h-full" />}>
                    <McpListPage />
                  </Suspense>
                ),
              },
              {
                path: 'mcp/new',
                element: (
                  <Suspense fallback={<Loading loading className="h-full" />}>
                    <McpEditorPage />
                  </Suspense>
                ),
              },
              {
                path: 'mcp/:serverId',
                element: (
                  <Suspense fallback={<Loading loading className="h-full" />}>
                    <McpEditorPage />
                  </Suspense>
                ),
              },
              {
                path: 'settings/memory',
                element: (
                  <Suspense fallback={<Loading loading className="h-full" />}>
                    <MemorySettingsPage />
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
