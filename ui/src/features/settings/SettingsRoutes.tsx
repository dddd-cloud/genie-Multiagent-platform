import React, { Suspense } from 'react';
import { Navigate, Route, Routes, useParams } from 'react-router-dom';
import { Loading } from '@/components';
import SettingsLayout from './SettingsLayout';

const AgentListPage = React.lazy(
  () => import('@/features/phase2/agents/AgentListPage'),
);
const AgentEditorPage = React.lazy(
  () => import('@/features/phase2/agents/AgentEditorPage'),
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
const ModelSettingsPage = React.lazy(
  () => import('@/features/settings/pages/ModelSettingsPage'),
);
const PreferencesPage = React.lazy(
  () => import('@/features/settings/PreferencesPage'),
);
const AccountPage = React.lazy(() => import('@/features/settings/AccountPage'));

function Page(element: React.ReactNode) {
  return (
    <Suspense fallback={<Loading loading className="h-full min-h-[240px]" />}>
      {element}
    </Suspense>
  );
}

function RedirectTo({ to }: { to: string }) {
  const params = useParams();
  const path = to.replace(/:([A-Za-z]+)/g, (_, key: string) =>
    encodeURIComponent(String(params[key] ?? '')),
  );
  return <Navigate to={path} replace />;
}

export default function SettingsRoutes() {
  return (
    <Routes>
      <Route path="/app/agents" element={<Navigate to="/app/settings/agents" replace />} />
      <Route
        path="/app/agents/new"
        element={<Navigate to="/app/settings/agents/new" replace />}
      />
      <Route
        path="/app/agents/:agentId"
        element={<RedirectTo to="/app/settings/agents/:agentId" />}
      />
      <Route path="/app/skills" element={<Navigate to="/app/settings/skills" replace />} />
      <Route
        path="/app/skills/new"
        element={<Navigate to="/app/settings/skills/new" replace />}
      />
      <Route
        path="/app/skills/:skillId"
        element={<RedirectTo to="/app/settings/skills/:skillId" />}
      />
      <Route path="/app/mcp" element={<Navigate to="/app/settings/mcp" replace />} />
      <Route
        path="/app/mcp/new"
        element={<Navigate to="/app/settings/mcp/new" replace />}
      />
      <Route
        path="/app/mcp/:serverId"
        element={<RedirectTo to="/app/settings/mcp/:serverId" />}
      />
      <Route path="/app/settings" element={<SettingsLayout />}>
        <Route index element={<Navigate to="models" replace />} />
        <Route path="models" element={Page(<ModelSettingsPage />)} />
        <Route path="agents" element={Page(<AgentListPage />)} />
        <Route path="agents/new" element={Page(<AgentEditorPage />)} />
        <Route path="agents/:agentId" element={Page(<AgentEditorPage />)} />
        <Route path="skills" element={Page(<SkillListPage />)} />
        <Route path="skills/new" element={Page(<SkillEditorPage />)} />
        <Route path="skills/:skillId" element={Page(<SkillEditorPage />)} />
        <Route path="mcp" element={Page(<McpListPage />)} />
        <Route path="mcp/new" element={Page(<McpEditorPage />)} />
        <Route path="mcp/:serverId" element={Page(<McpEditorPage />)} />
        <Route path="memory" element={Page(<MemorySettingsPage />)} />
        <Route path="preferences" element={Page(<PreferencesPage />)} />
        <Route path="profile" element={<Navigate to="/app/settings/account" replace />} />
        <Route path="account" element={Page(<AccountPage />)} />
      </Route>
      <Route path="*" element={<Navigate to="/app/settings/models" replace />} />
    </Routes>
  );
}
