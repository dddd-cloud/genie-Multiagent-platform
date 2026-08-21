import { memo, useContext, useMemo, useState, type ReactNode } from 'react';
import {
  NavigationType,
  parsePath,
  resolvePath,
  Route,
  Routes,
  UNSAFE_LocationContext,
  UNSAFE_NavigationContext,
  UNSAFE_RouteContext,
  type Location,
  type Navigator,
  type To,
} from 'react-router-dom';
import { Modal } from 'antd';
import { CloseOutlined } from '@ant-design/icons';
import SkillListPage from '@/features/phase2/skills/SkillListPage';
import SkillEditorPage from '@/features/phase2/skills/SkillEditorPage';
import McpListPage from '@/features/phase2/mcp/McpListPage';
import McpEditorPage from '@/features/phase2/mcp/McpEditorPage';
import AgentListPage from '@/features/phase2/agents/AgentListPage';
import AgentEditorPage from '@/features/phase2/agents/AgentEditorPage';
import TeamListPage from '@/features/phase2/teams/TeamListPage';
import TeamEditorPage from '@/features/phase2/teams/TeamEditorPage';
import {
  AGENTS_LIBRARY_PATH,
  MCP_LIBRARY_PATH,
  SKILLS_LIBRARY_PATH,
  TEAMS_LIBRARY_PATH,
} from './paths';

export type LibraryKind = 'skills' | 'mcp' | 'agents' | 'teams';

export type LibraryModalProps = {
  open: boolean;
  session: number;
  kind: LibraryKind;
  initialPath: string;
  initialState?: unknown;
  onClose: () => void;
};

const EMPTY_ROUTE_CONTEXT = {
  outlet: null,
  matches: [],
  isDataRoute: false,
};

function defaultPath(kind: LibraryKind): string {
  if (kind === 'mcp') {
    return MCP_LIBRARY_PATH;
  }
  if (kind === 'agents') {
    return AGENTS_LIBRARY_PATH;
  }
  if (kind === 'teams') {
    return TEAMS_LIBRARY_PATH;
  }
  return SKILLS_LIBRARY_PATH;
}

function resolveTo(to: To, currentPathname: string) {
  const target = typeof to === 'string' ? parsePath(to) : to;
  return resolvePath(target, currentPathname);
}

function LibraryInnerRouter({
  initialPath,
  initialState,
  children,
}: {
  initialPath: string;
  initialState?: unknown;
  children: ReactNode;
}) {
  const parentNavigation = useContext(UNSAFE_NavigationContext);
  const [location, setLocation] = useState<Location>(() => ({
    pathname: initialPath,
    search: '',
    hash: '',
    state: initialState,
    key: 'library',
  }));

  const navigator = useMemo<Navigator>(
    () => ({
      createHref: (to) => resolveTo(to, location.pathname).pathname,
      encodeLocation: (to) => {
        const resolved = resolveTo(to, location.pathname);
        return { pathname: resolved.pathname, search: resolved.search || '', hash: '' };
      },
      go: () => undefined,
      push: (to, state) => {
        const resolved = resolveTo(to, location.pathname);
        setLocation({
          pathname: resolved.pathname,
          search: resolved.search || '',
          hash: '',
          state,
          key: `library-${Date.now()}`,
        });
      },
      replace: (to, state) => {
        const resolved = resolveTo(to, location.pathname);
        setLocation({
          pathname: resolved.pathname,
          search: resolved.search || '',
          hash: '',
          state,
          key: `library-${Date.now()}`,
        });
      },
    }),
    [location.pathname],
  );

  const navigation = useMemo(
    () => ({
      ...parentNavigation,
      navigator,
      static: false,
    }),
    [navigator, parentNavigation],
  );

  return (
    <UNSAFE_LocationContext.Provider
      value={{ location, navigationType: NavigationType.Push }}
    >
      <UNSAFE_NavigationContext.Provider value={navigation}>
        <UNSAFE_RouteContext.Provider value={EMPTY_ROUTE_CONTEXT}>
          {children}
        </UNSAFE_RouteContext.Provider>
      </UNSAFE_NavigationContext.Provider>
    </UNSAFE_LocationContext.Provider>
  );
}

const LibraryModal: GenieType.FC<LibraryModalProps> = memo(
  ({ open, session, kind, initialPath, initialState, onClose }) => {
    const pathname = initialPath || defaultPath(kind);

    return (
      <Modal
        open={open}
        onCancel={onClose}
        footer={null}
        closable={false}
        maskClosable
        centered
        width={960}
        destroyOnHidden
        data-testid="library-modal"
        className="settings-modal"
        styles={{
          content: { padding: 0, borderRadius: 16, overflow: 'hidden' },
          body: { padding: 0 },
        }}
      >
        {open ? (
          <div className="flex h-[min(80vh,720px)] min-h-[480px] flex-col bg-surface">
            <div className="flex shrink-0 items-center px-12 pt-12 pb-8">
              <button
                type="button"
                aria-label="关闭"
                data-testid="library-modal-close"
                onClick={onClose}
                className="flex h-28 w-28 items-center justify-center rounded-[8px] text-text-secondary hover:bg-[#F5F5F7] transition-colors"
              >
                <CloseOutlined className="text-[14px]" />
              </button>
            </div>
            <div className="min-h-0 flex-1 overflow-auto px-28 pb-24">
              <LibraryInnerRouter
                key={session}
                initialPath={pathname}
                initialState={initialState}
              >
                <Routes>
                  <Route path={SKILLS_LIBRARY_PATH} element={<SkillListPage />} />
                  <Route
                    path={`${SKILLS_LIBRARY_PATH}/new`}
                    element={<SkillEditorPage />}
                  />
                  <Route
                    path={`${SKILLS_LIBRARY_PATH}/:skillId`}
                    element={<SkillEditorPage />}
                  />
                  <Route path={MCP_LIBRARY_PATH} element={<McpListPage />} />
                  <Route
                    path={`${MCP_LIBRARY_PATH}/new`}
                    element={<McpEditorPage />}
                  />
                  <Route
                    path={`${MCP_LIBRARY_PATH}/:serverId`}
                    element={<McpEditorPage />}
                  />
                  <Route path={AGENTS_LIBRARY_PATH} element={<AgentListPage />} />
                  <Route
                    path={`${AGENTS_LIBRARY_PATH}/new`}
                    element={<AgentEditorPage />}
                  />
                  <Route
                    path={`${AGENTS_LIBRARY_PATH}/:agentId`}
                    element={<AgentEditorPage />}
                  />
                  <Route path={TEAMS_LIBRARY_PATH} element={<TeamListPage />} />
                  <Route
                    path={`${TEAMS_LIBRARY_PATH}/new`}
                    element={<TeamEditorPage />}
                  />
                  <Route
                    path={`${TEAMS_LIBRARY_PATH}/:teamId`}
                    element={<TeamEditorPage />}
                  />
                </Routes>
              </LibraryInnerRouter>
            </div>
          </div>
        ) : null}
      </Modal>
    );
  },
);

LibraryModal.displayName = 'LibraryModal';

export default LibraryModal;
