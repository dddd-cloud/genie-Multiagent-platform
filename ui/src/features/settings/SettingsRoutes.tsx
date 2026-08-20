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
const MemorySettingsPage = React.lazy(
  () => import('@/features/phase2/localMemory/MemorySettingsPage'),
);
const ModelSettingsPage = React.lazy(
  () => import('@/features/settings/pages/ModelSettingsPage'),
);
const ModelEditorPage = React.lazy(
  () => import('@/features/settings/pages/ModelEditorPage'),
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
      <Route path="/app/settings" element={<SettingsLayout />}>
        <Route index element={<Navigate to="models" replace />} />
        <Route path="models" element={Page(<ModelSettingsPage />)} />
        <Route path="models/new" element={Page(<ModelEditorPage />)} />
        <Route path="models/:modelId" element={Page(<ModelEditorPage />)} />
        <Route path="agents" element={Page(<AgentListPage />)} />
        <Route path="agents/new" element={Page(<AgentEditorPage />)} />
        <Route path="agents/:agentId" element={Page(<AgentEditorPage />)} />
        <Route path="skills" element={<Navigate to="/app/marketplace" replace />} />
        <Route
          path="skills/new"
          element={<Navigate to="/app/marketplace" replace />}
        />
        <Route
          path="skills/:skillId"
          element={<Navigate to="/app/marketplace" replace />}
        />
        <Route path="mcp" element={<Navigate to="/app/marketplace?tab=connectors" replace />} />
        <Route
          path="mcp/new"
          element={<Navigate to="/app/marketplace?tab=connectors" replace />}
        />
        <Route
          path="mcp/:serverId"
          element={<Navigate to="/app/marketplace?tab=connectors" replace />}
        />
        <Route path="memory" element={Page(<MemorySettingsPage />)} />
        <Route path="preferences" element={Page(<PreferencesPage />)} />
        <Route path="profile" element={<Navigate to="/app/settings/account" replace />} />
        <Route path="account" element={Page(<AccountPage />)} />
      </Route>
      <Route path="*" element={<Navigate to="/app/settings/models" replace />} />
    </Routes>
  );
}
