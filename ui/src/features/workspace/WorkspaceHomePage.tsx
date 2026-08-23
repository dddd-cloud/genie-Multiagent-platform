import { useCallback, useMemo, useState } from 'react';
import { Outlet, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeftOutlined, PlusOutlined } from '@ant-design/icons';
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
import { WorkspaceFilesSection, ActiveWorkspaceFilesSection } from './WorkspaceFilesSection';
import { useResizablePane } from './useResizablePane';

const LEFT_DEFAULT_WIDTH = 260;
const RIGHT_DEFAULT_WIDTH = 280;

function Divider({
  onPointerDown,
  dragging,
}: {
  readonly onPointerDown: (event: React.PointerEvent) => void;
  readonly dragging: boolean;
}) {
  return (
    <div
      role="separator"
      aria-orientation="vertical"
      onPointerDown={onPointerDown}
      className={`relative h-full w-[1px] shrink-0 cursor-col-resize bg-border transition-colors ${dragging ? 'bg-[#3562FA]' : 'hover:bg-[#3562FA]'}`}
    >
      {/* Wider invisible hit area than the visible 1px line, so it's easy to grab. */}
      <div className="absolute inset-y-0 -left-2 -right-2" />
    </div>
  );
}

export function WorkspaceHomePage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { workspaceId: activeWorkspaceId, conversationId: activeConversationId } = useParams<{
    workspaceId?: string;
    conversationId?: string;
  }>();
  const [workspaces, setWorkspaces] = useState(() => (user?.id ? loadUserWorkspaces(user.id) : []));
  const left = useResizablePane({
    defaultWidth: LEFT_DEFAULT_WIDTH,
    storageKey: 'joyagent.workspacePage.leftWidth',
    direction: 'grow-right',
  });
  const right = useResizablePane({
    defaultWidth: RIGHT_DEFAULT_WIDTH,
    storageKey: 'joyagent.workspacePage.rightWidth',
    direction: 'grow-left',
  });

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

  const leftRail = (
    <div className="flex h-full flex-col">
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
      <div className="shrink-0 border-t border-border p-8">
        <button
          type="button"
          data-testid="workspace-exit"
          className="flex w-full items-center gap-8 rounded-[8px] px-10 py-7 text-[14px] text-text-primary hover:bg-[#F5F5F7]"
          onClick={() => navigate('/app')}
        >
          <ArrowLeftOutlined className="text-[14px] text-text-secondary" />
          <span>返回</span>
        </button>
      </div>
    </div>
  );

  const middlePane = (
    <div className="flex h-full min-w-0 flex-1 flex-col overflow-hidden bg-surface">
      {activeWorkspace ? (
        <Outlet />
      ) : (
        <div className="flex h-full w-full items-center justify-center text-text-secondary">
          在左侧选择或新建一个工作区开始
        </div>
      )}
    </div>
  );

  const rightRail = (
    <div className="h-full overflow-auto">
      {workspaces.map((workspace) =>
        workspace.id === activeWorkspaceId ? (
          <ActiveWorkspaceFilesSection key={workspace.id} defaultExpanded />
        ) : (
          <WorkspaceFilesSection
            key={workspace.id}
            userId={user.id}
            workspace={workspace}
            defaultExpanded={false}
            isActive={false}
          />
        ),
      )}
    </div>
  );

  const body = (
    <div className="flex h-full w-full bg-surface">
      <div className="h-full shrink-0 overflow-hidden" style={{ width: left.width }}>
        {leftRail}
      </div>
      <Divider onPointerDown={left.startDrag} dragging={left.dragging} />
      {middlePane}
      <Divider onPointerDown={right.startDrag} dragging={right.dragging} />
      <div className="h-full shrink-0 overflow-hidden" style={{ width: right.width }}>
        {rightRail}
      </div>
    </div>
  );

  return activeWorkspace ? (
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
      {body}
    </WorkspaceProvider>
  ) : (
    body
  );
}

export default WorkspaceHomePage;
