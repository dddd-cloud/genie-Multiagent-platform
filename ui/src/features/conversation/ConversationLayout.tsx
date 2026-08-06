import {
  createContext,
  memo,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useReducer,
} from 'react';
import { Outlet, useNavigate, useParams } from 'react-router-dom';
import { Button, message } from 'antd';
import { FormOutlined } from '@ant-design/icons';
import type { ConversationListItem } from '@/contracts';
import { useAuth } from '@/features/auth/useAuth';
import { isPhase2Enabled } from '@/features/phase2/executionMode/featureFlag';
import LocalMemoryProvider from '@/features/phase2/localMemory/LocalMemoryProvider';
import Phase2Navigation from '@/layout/Phase2Navigation';
import { MvpApiError } from '@/services/apiError';
import {
  createConversation,
  deleteConversation,
  listConversations,
  updateConversation,
} from './api';
import ConversationSidebar from './ConversationSidebar';
import {
  conversationReducer,
  initialConversationListState,
} from './conversationReducer';

const PAGE_SIZE = 20;

function isAuthRequired(err: unknown): err is MvpApiError {
  return err instanceof MvpApiError && err.code === 'AUTH_REQUIRED';
}

export interface ConversationLayoutContextValue {
  reload: () => Promise<void>;
  upsert: (item: ConversationListItem) => void;
  remove: (id: string) => void;
  items: ConversationListItem[];
}

const ConversationLayoutContext =
  createContext<ConversationLayoutContextValue | null>(null);

export function useConversationLayout(): ConversationLayoutContextValue | null {
  return useContext(ConversationLayoutContext);
}

function toListItem(
  item:
    | ConversationListItem
    | (ConversationListItem & { lastMessagePreview?: string | null }),
): ConversationListItem {
  return {
    id: item.id,
    title: item.title,
    lastMessageAt: item.lastMessageAt,
    createdAt: item.createdAt,
    updatedAt: item.updatedAt,
    lastMessagePreview: item.lastMessagePreview ?? null,
  };
}

const ConversationLayout: GenieType.FC = memo(() => {
  const [state, dispatch] = useReducer(
    conversationReducer,
    initialConversationListState,
  );
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { conversationId } = useParams<{ conversationId?: string }>();

  const loadPage = useCallback(async (page: number, more = false) => {
    dispatch({
      type: 'LOAD_START',
      more
    });
    try {
      const data = await listConversations(page, PAGE_SIZE);
      if (!data) {
        throw new Error('会话列表为空');
      }
      if (more) {
        dispatch({
          type: 'APPEND_SUCCESS',
          items: data.items,
          page: data.page,
          hasMore: data.hasMore,
        });
      } else {
        dispatch({
          type: 'LOAD_SUCCESS',
          items: data.items,
          page: data.page,
          hasMore: data.hasMore,
        });
      }
    } catch (err: unknown) {
      if (isAuthRequired(err)) {
        throw err;
      }
      if (err instanceof MvpApiError && err.code === 'ACCESS_DENIED') {
        message.error('无权限访问会话列表');
      }
      const msg =
        err instanceof MvpApiError
          ? err.message
          : err instanceof Error
            ? err.message
            : '加载会话失败';
      dispatch({
        type: 'LOAD_FAILURE',
        error: msg,
        more
      });
    }
  }, []);

  const reload = useCallback(async () => {
    await loadPage(1, false);
  }, [loadPage]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const upsert = useCallback((item: ConversationListItem) => {
    dispatch({
      type: 'UPSERT',
      item: toListItem(item)
    });
  }, []);

  const remove = useCallback((id: string) => {
    dispatch({
      type: 'REMOVE',
      id
    });
  }, []);

  const handleNew = useCallback(async () => {
    try {
      const created = await createConversation(null);
      if (!created) {
        message.error('创建会话失败');
        return;
      }
      const listItem = toListItem({
        ...created,
        lastMessagePreview: null,
      });
      dispatch({
        type: 'UPSERT',
        item: listItem
      });
      navigate(`/app/chat/${created.id}`);
    } catch (err: unknown) {
      if (isAuthRequired(err)) {
        throw err;
      }
      if (err instanceof MvpApiError && err.code === 'ACCESS_DENIED') {
        message.error('无权限创建会话');
        return;
      }
      message.error(
        err instanceof MvpApiError ? err.message : '创建会话失败',
      );
    }
  }, [navigate]);

  const handleRename = useCallback(
    async (id: string, title: string) => {
      try {
        const updated = await updateConversation(id, title);
        if (!updated) {
          message.error('重命名失败');
          return;
        }
        const existing = state.items.find((item) => item.id === id);
        dispatch({
          type: 'UPSERT',
          item: toListItem({
            ...updated,
            lastMessagePreview: existing?.lastMessagePreview ?? null,
          }),
        });
      } catch (err: unknown) {
        if (isAuthRequired(err)) {
          throw err;
        }
        if (err instanceof MvpApiError) {
          if (err.code === 'ACCESS_DENIED') {
            message.error('无权限重命名该会话');
            return;
          }
          if (err.httpStatus === 404 || err.code === 'RESOURCE_NOT_FOUND') {
            dispatch({
              type: 'REMOVE',
              id
            });
            if (conversationId === id) {
              navigate('/app');
            }
            message.error('会话不存在或已删除');
            return;
          }
        }
        message.error(
          err instanceof MvpApiError ? err.message : '重命名失败',
        );
        throw err;
      }
    },
    [conversationId, navigate, state.items],
  );

  const handleDelete = useCallback(
    async (id: string) => {
      try {
        await deleteConversation(id);
        dispatch({
          type: 'REMOVE',
          id
        });
        if (conversationId === id) {
          navigate('/app');
        }
      } catch (err: unknown) {
        if (isAuthRequired(err)) {
          throw err;
        }
        if (err instanceof MvpApiError) {
          if (err.code === 'ACCESS_DENIED') {
            message.error('无权限删除该会话');
            return;
          }
          if (err.httpStatus === 404 || err.code === 'RESOURCE_NOT_FOUND') {
            dispatch({
              type: 'REMOVE',
              id
            });
            if (conversationId === id) {
              navigate('/app');
            }
            return;
          }
          if (err.httpStatus === 409 || err.code === 'CONVERSATION_BUSY') {
            message.error('当前会话正在执行，暂不能删除');
            return;
          }
        }
        message.error(
          err instanceof MvpApiError ? err.message : '删除会话失败',
        );
      }
    },
    [conversationId, navigate],
  );

  const handleLoadMore = useCallback(() => {
    if (!state.hasMore || state.loadingMore) {
      return;
    }
    void loadPage(state.page + 1, true);
  }, [loadPage, state.hasMore, state.loadingMore, state.page]);

  const handleSelect = useCallback(
    (id: string) => {
      navigate(`/app/chat/${id}`);
    },
    [navigate],
  );

  const handleLogout = useCallback(async () => {
    await logout();
  }, [logout]);

  const contextValue = useMemo<ConversationLayoutContextValue>(
    () => ({
      reload,
      upsert,
      remove,
      items: state.items,
    }),
    [reload, remove, state.items, upsert],
  );

  const layout = (
    <ConversationLayoutContext.Provider value={contextValue}>
      <div className="h-full w-full flex bg-page">
        <div className="h-full w-[272px] shrink-0 flex flex-col bg-sidebar border-r border-border">
          <div className="w-full px-14 py-12 border-b border-border flex items-center justify-between gap-8">
            <div className="min-w-0">
              <div className="text-[12px] text-text-secondary leading-[18px]">
                当前用户
              </div>
              <div className="text-[14px] font-medium text-text-primary truncate leading-[22px]">
                {user?.displayName || user?.username || '—'}
              </div>
            </div>
            <Button size="small" onClick={handleLogout}>
              退出
            </Button>
          </div>
          <div className="w-full px-10 pt-10 pb-8 border-b border-border">
            <button
              type="button"
              onClick={handleNew}
              aria-label="新会话"
              className="w-full flex items-center gap-8 rounded-[8px] px-10 py-8 text-[14px] leading-[22px] text-text-primary hover:bg-[#F5F5F7] transition-colors duration-150"
            >
              <FormOutlined className="text-[15px] text-text-secondary" />
              <span>新会话</span>
            </button>
          </div>
          {isPhase2Enabled() ? <Phase2Navigation /> : null}
          <div className="flex-1 min-h-0 w-full">
            <ConversationSidebar
              state={state}
              onRename={handleRename}
              onDelete={handleDelete}
              onLoadMore={handleLoadMore}
              onRetry={() => void reload()}
              onSelect={handleSelect}
            />
          </div>
        </div>
        <div className="flex-1 min-w-0 h-full overflow-hidden bg-surface">
          <Outlet />
        </div>
      </div>
    </ConversationLayoutContext.Provider>
  );

  if (user?.id) {
    return (
      <LocalMemoryProvider userId={user.id}>{layout}</LocalMemoryProvider>
    );
  }

  return layout;
});

ConversationLayout.displayName = 'ConversationLayout';

export default ConversationLayout;
