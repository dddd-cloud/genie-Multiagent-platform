import { useCallback, useMemo, useState } from 'react';
import { Outlet, useNavigate, useParams } from 'react-router-dom';
import { MenuFoldOutlined, MenuUnfoldOutlined, PlusOutlined } from '@ant-design/icons';
import { message } from 'antd';
import { useAuth } from '@/features/auth/useAuth';
import {
  createUserWorkspace,
  deleteUserWorkspace,
  loadUserWorkspaces,
  renameUserWorkspace,
} from '@/platform/workspace/catalog';
import { WorkspaceProvider } from './WorkspaceProvider';
import { WorkspaceConversationsSection } from './WorkspaceConversationsSection';
import { WorkspaceFilesSection } from './WorkspaceFilesSection';

const LEFT_COLLAPSE_KEY = 'joyagent.workspacePage.leftCollapsed';
const RIGHT_COLLAPSE_KEY = 'joyagent.workspacePage.rightCollapsed';

function readStoredBoolean(key: string): boolean {
  try {
    return localStorage.getItem(key) === '1';
  } catch {
    return false;
  }
}

function writeStoredBoolean(key: string, value: boolean): void {
  try {
    localStorage.setItem(key, value ? '1' : '0');
  } catch {
    // Best-effort only; the panel still toggles for this session.
  }
}

export function WorkspaceHomePage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { workspaceId: activeWorkspaceId, conversationId: activeConversationId } = useParams<{
    workspaceId?: string;
    conversationId?: string;
  }>();
  const [workspaces, setWorkspaces] = useState(() => (user?.id ? loadUserWorkspaces(user.id) : []));
  const [leftCollapsed, setLeftCollapsed] = useState(() => readStoredBoolean(LEFT_COLLAPSE_KEY));
  const [rightCollapsed, setRightCollapsed] = useState(() => readStoredBoolean(RIGHT_COLLAPSE_KEY));

  const toggleLeft = () => {
    setLeftCollapsed((value) => {
      writeStoredBoolean(LEFT_COLLAPSE_KEY, !value);
      return !value;
    });
  };
  const toggleRight = () => {
    setRightCollapsed((value) => {
      writeStoredBoolean(RIGHT_COLLAPSE_KEY, !value);
      return !value;
    });
  };

  const handleCreateWorkspace = useCallback(() => {
    if (!user?.id) return;
    try {
      const next = createUserWorkspace(user.id, workspaces, `工作区 ${workspaces.length + 1}`);
      setWorkspaces(next);
      navigate(`/app/workspace/${next[next.length - 1].id}`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '新建工作区失败');
    }
  }, [navigate, user?.id, workspaces]);

  const handleRenameWorkspace = useCallback(
    (workspaceId: string, name: string) => {
      if (!user?.id || !name) return;
      try {
        setWorkspaces(renameUserWorkspace(user.id, workspaces, workspaceId, name));
      } catch (error) {
        message.error(error instanceof Error ? error.message : '重命名失败');
      }
    },
    [user?.id, workspaces],
  );

  const handleDeleteWorkspace = useCallback(
    (workspaceId: string) => {
      if (!user?.id) return;
      try {
        const next = deleteUserWorkspace(user.id, workspaces, workspaceId);
        setWorkspaces(next);
        if (activeWorkspaceId === workspaceId) {
          navigate('/app/workspace');
        }
      } catch (error) {
        message.error(error instanceof Error ? error.message : '删除工作区失败');
      }
    },
    [activeWorkspaceId, navigate, user?.id, workspaces],
  );

  const activeWorkspace = useMemo(
    () => workspaces.find((item) => item.id === activeWorkspaceId) ?? null,
    [activeWorkspaceId, workspaces],
  );

  if (!user?.id) {
    return null;
  }

  const middlePane = (
    <div className="flex h-full min-w-0 flex-1 flex-col bg-surface">
      {activeWorkspace ? (
        <Outlet />
      ) : (
        <div className="flex h-full w-full items-center justify-center text-text-secondary">
          在左侧选择或新建一个工作区开始
        </div>
      )}
    </div>
  );

  return (
    <div className="flex h-full w-full bg-surface">
      <div
        className={`h-full shrink-0 overflow-hidden border-r border-border transition-[width] duration-200 ${
          leftCollapsed ? 'w-0' : 'w-[20%] min-w-[220px] max-w-[320px]'
        }`}
      >
        <div className="flex h-full w-[220px] min-w-[220px] flex-col">
          <div className="flex shrink-0 items-center justify-between gap-8 px-12 py-12">
            <button
              type="button"
              className="flex flex-1 items-center justify-center gap-6 rounded-[8px] border border-border px-8 py-6 text-[13px] text-text-primary hover:bg-[#F5F5F7]"
              onClick={handleCreateWorkspace}
            >
              <PlusOutlined /> 新建工作区
            </button>
          </div>
          <div className="min-h-0 flex-1 overflow-auto">
            {workspaces.map((workspace) => (
              <WorkspaceConversationsSection
                key={workspace.id}
                workspace={workspace}
                defaultExpanded={workspace.id === activeWorkspaceId}
                isActive={workspace.id === activeWorkspaceId}
                activeConversationId={workspace.id === activeWorkspaceId ? activeConversationId : undefined}
                onRenameWorkspace={(name) => handleRenameWorkspace(workspace.id, name)}
                onDeleteWorkspace={() => handleDeleteWorkspace(workspace.id)}
                canDeleteWorkspace={workspaces.length > 1}
              />
            ))}
          </div>
        </div>
      </div>
      <button
        type="button"
        aria-label={leftCollapsed ? '展开左侧栏' : '收起左侧栏'}
        className="flex h-full w-16 shrink-0 items-center justify-center border-r border-border text-text-secondary hover:bg-[#F5F5F7]"
        onClick={toggleLeft}
      >
        {leftCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
      </button>

      {activeWorkspace ? (
        <WorkspaceProvider
          userId={user.id}
          workspaceId={activeWorkspace.id}
          workspaces={workspaces}
          activeWorkspace={activeWorkspace}
          selectWorkspace={() => {}}
          createWorkspace={() => {}}
          renameWorkspace={(name) => handleRenameWorkspace(activeWorkspace.id, name)}
          deleteWorkspace={() => handleDeleteWorkspace(activeWorkspace.id)}
        >
          {middlePane}
        </WorkspaceProvider>
      ) : (
        middlePane
      )}

      <button
        type="button"
        aria-label={rightCollapsed ? '展开右侧栏' : '收起右侧栏'}
        className="flex h-full w-16 shrink-0 items-center justify-center border-l border-border text-text-secondary hover:bg-[#F5F5F7]"
        onClick={toggleRight}
      >
        {rightCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
      </button>
      <div
        className={`h-full shrink-0 overflow-hidden border-l border-border transition-[width] duration-200 ${
          rightCollapsed ? 'w-0' : 'w-[20%] min-w-[240px] max-w-[360px]'
        }`}
      >
        <div className="h-full w-[240px] min-w-[240px] overflow-auto">
          {workspaces.map((workspace) => (
            <WorkspaceFilesSection
              key={workspace.id}
              userId={user.id}
              workspace={workspace}
              defaultExpanded={workspace.id === activeWorkspaceId}
              isActive={workspace.id === activeWorkspaceId}
            />
          ))}
        </div>
      </div>
    </div>
  );
}

export default WorkspaceHomePage;
