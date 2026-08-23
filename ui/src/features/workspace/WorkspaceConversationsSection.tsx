import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { DownOutlined, MessageOutlined, PlusOutlined, RightOutlined } from '@ant-design/icons';
import { Dropdown, Input, message, type MenuProps } from 'antd';
import type { UserWorkspace } from '@/platform/workspace/catalog';
import type { ConversationListItem } from '@/contracts';
import { deleteConversation, listConversations, updateConversation } from '@/features/conversation/api';

export interface WorkspaceConversationsSectionProps {
  readonly workspace: UserWorkspace;
  readonly defaultExpanded: boolean;
  readonly isActive: boolean;
  readonly activeConversationId?: string;
  readonly onRenameWorkspace: (name: string) => void;
  readonly onDeleteWorkspace: () => void;
  readonly canDeleteWorkspace: boolean;
}

export function WorkspaceConversationsSection({
  workspace,
  defaultExpanded,
  isActive,
  activeConversationId,
  onRenameWorkspace,
  onDeleteWorkspace,
  canDeleteWorkspace,
}: WorkspaceConversationsSectionProps) {
  const navigate = useNavigate();
  const [expanded, setExpanded] = useState(defaultExpanded);
  const [conversations, setConversations] = useState<ConversationListItem[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [renamingWorkspace, setRenamingWorkspace] = useState<string | null>(null);
  const [renamingConversation, setRenamingConversation] = useState<{ id: string; value: string } | null>(null);

  const refresh = useCallback(async () => {
    try {
      const response = await listConversations(1, 50, workspace.id);
      setConversations(response?.items ?? []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '会话列表加载失败');
    } finally {
      setLoaded(true);
    }
  }, [workspace.id]);

  useEffect(() => {
    if (expanded && !loaded) void refresh();
  }, [expanded, loaded, refresh]);

  useEffect(() => {
    // A conversation just created elsewhere on this page should show up without a manual refresh.
    if (isActive && expanded) void refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeConversationId]);

  const workspaceMenu: MenuProps['items'] = [
    { key: 'rename', label: '重命名工作区', onClick: () => setRenamingWorkspace(workspace.name) },
    {
      key: 'delete',
      label: '删除工作区',
      danger: true,
      disabled: !canDeleteWorkspace,
      onClick: () => {
        if (window.confirm(`删除工作区"${workspace.name}"？其中的会话记录不会被删除，但会与工作区失去关联。`)) {
          onDeleteWorkspace();
        }
      },
    },
  ];

  const submitRenameConversation = async (item: ConversationListItem) => {
    if (!renamingConversation) return;
    const value = renamingConversation.value.trim();
    setRenamingConversation(null);
    if (!value || value === item.title) return;
    try {
      await updateConversation(item.id, value);
      setConversations((prev) =>
        prev.map((conversation) => (conversation.id === item.id ? { ...conversation, title: value } : conversation)),
      );
    } catch (error) {
      message.error(error instanceof Error ? error.message : '重命名失败');
    }
  };

  const conversationMenu = (item: ConversationListItem): MenuProps['items'] => [
    { key: 'rename', label: '重命名', onClick: () => setRenamingConversation({ id: item.id, value: item.title }) },
    {
      key: 'delete',
      label: '删除',
      danger: true,
      onClick: () => {
        if (!window.confirm(`删除会话"${item.title}"？此操作无法撤销。`)) return;
        void deleteConversation(item.id)
          .then(() => {
            setConversations((prev) => prev.filter((conversation) => conversation.id !== item.id));
            if (activeConversationId === item.id) navigate(`/app/workspace/${workspace.id}`);
          })
          .catch((error: unknown) => message.error(error instanceof Error ? error.message : '删除失败'));
      },
    },
  ];

  return (
    <div className="border-b border-border last:border-b-0">
      <div className={`group flex w-full items-center gap-6 px-8 py-8 hover:bg-[#F5F5F7] ${isActive ? 'bg-[#F5F7FF]' : ''}`}>
        <button
          type="button"
          className="flex min-w-0 flex-1 items-center gap-6 text-left text-[13px] font-medium text-text-primary"
          onClick={() => setExpanded((value) => !value)}
        >
          {expanded ? <DownOutlined className="text-[10px] text-text-secondary" /> : <RightOutlined className="text-[10px] text-text-secondary" />}
          {renamingWorkspace !== null ? (
            <Input
              autoFocus
              size="small"
              value={renamingWorkspace}
              onChange={(event) => setRenamingWorkspace(event.target.value)}
              onClick={(event) => event.stopPropagation()}
              onPressEnter={() => {
                onRenameWorkspace(renamingWorkspace.trim());
                setRenamingWorkspace(null);
              }}
              onBlur={() => {
                onRenameWorkspace(renamingWorkspace.trim());
                setRenamingWorkspace(null);
              }}
            />
          ) : (
            <span className="min-w-0 flex-1 truncate">{workspace.name}</span>
          )}
        </button>
        <button
          type="button"
          aria-label="新建会话"
          className="flex h-20 w-20 shrink-0 items-center justify-center rounded-[4px] text-text-secondary hover:bg-[#E9E9EC]"
          onClick={() => navigate(`/app/workspace/${workspace.id}`)}
        >
          <PlusOutlined className="text-[12px]" />
        </button>
        <Dropdown menu={{ items: workspaceMenu }} trigger={['click']}>
          <button
            type="button"
            className="invisible flex h-20 w-20 shrink-0 items-center justify-center rounded-[4px] text-text-secondary hover:bg-[#E9E9EC] group-hover:visible"
          >
            ⋯
          </button>
        </Dropdown>
      </div>
      {expanded ? (
        <div className="pb-6">
          {conversations.length === 0 ? (
            <p className="px-24 py-6 text-[12px] text-text-secondary">还没有会话</p>
          ) : (
            conversations.map((item) => {
              const active = item.id === activeConversationId;
              const isRenaming = renamingConversation?.id === item.id;
              return (
                <div
                  key={item.id}
                  className={`group flex w-full items-center gap-6 px-24 py-6 text-[13px] hover:bg-[#F5F5F7] ${active ? 'bg-[#EFF3FF] text-[#3562FA]' : 'text-text-primary'}`}
                >
                  <MessageOutlined className="shrink-0 text-[12px] text-text-secondary" />
                  {isRenaming ? (
                    <Input
                      autoFocus
                      size="small"
                      value={renamingConversation.value}
                      onChange={(event) => setRenamingConversation({ ...renamingConversation, value: event.target.value })}
                      onPressEnter={() => void submitRenameConversation(item)}
                      onBlur={() => void submitRenameConversation(item)}
                    />
                  ) : (
                    <button
                      type="button"
                      className="min-w-0 flex-1 truncate text-left"
                      onClick={() => navigate(`/app/workspace/${workspace.id}/chat/${item.id}`)}
                    >
                      {item.title}
                    </button>
                  )}
                  <Dropdown menu={{ items: conversationMenu(item) }} trigger={['click']}>
                    <button
                      type="button"
                      className="invisible flex h-18 w-18 shrink-0 items-center justify-center rounded-[4px] text-text-secondary hover:bg-[#E9E9EC] group-hover:visible"
                      onClick={(event) => event.stopPropagation()}
                    >
                      ⋯
                    </button>
                  </Dropdown>
                </div>
              );
            })
          )}
        </div>
      ) : null}
    </div>
  );
}

export default WorkspaceConversationsSection;
