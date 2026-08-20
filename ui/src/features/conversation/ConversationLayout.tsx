import {
  createContext,
  lazy,
  memo,
  Suspense,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useReducer,
  useRef,
  useState,
} from 'react';
import { Outlet, useLocation, useNavigate, useParams } from 'react-router-dom';
import { Tooltip, Spin, message } from 'antd';
import {
  FormOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons';
import type { ConversationListItem } from '@/contracts';
import { useAuth } from '@/features/auth/useAuth';
import LocalMemoryProvider from '@/features/phase2/localMemory/LocalMemoryProvider';
import { useUserSettings } from '@/features/userSettings/useUserSettings';
import SidebarUserMenu from '@/features/userSettings/SidebarUserMenu';
import Logo from '@/components/Logo';
import AppNavigation from '@/layout/AppNavigation';
import SettingsModal from '@/features/settings/SettingsModal';
import { SettingsModalProvider } from '@/features/settings/SettingsModalContext';
import { MvpApiError } from '@/services/apiError';
import {
  deleteConversation,
  listConversations,
  updateConversation,
} from './api';
import ConversationSidebar from './ConversationSidebar';
import {
  conversationReducer,
  initialConversationListState,
} from './conversationReducer';
import {
  isChatSurfacePath,
  isNewConversationPath,
} from './newConversationPath';
import { peekConversationDraft } from './pendingConversationDraft';
import {
  isUnusedConversation,
  unusedConversationIds,
} from './unusedConversation';

const ConversationPage = lazy(() => import('./ConversationPage'));

const PAGE_SIZE = 20;

function isAuthRequired(err: unknown): err is MvpApiError {
  return err instanceof MvpApiError && err.code === 'AUTH_REQUIRED';
}

export interface ConversationLayoutContextValue {
  reload: () => Promise<void>;
  upsert: (item: ConversationListItem) => void;
  remove: (id: string) => void;
  items: ConversationListItem[];
  discardUnusedDrafts: (exceptId?: string | null) => Promise<void>;
  touch: (id: string) => void;
  privacyMode: boolean;
  /** Bumped when the user clicks 新会话 so the composer remounts even if already on /app. */
  composerEpoch: number;
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
    privacyMode: item.privacyMode === true,
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
  const { user } = useAuth();
  const { preferences, status: preferencesStatus } = useUserSettings();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const { conversationId } = useParams<{ conversationId?: string }>();
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [composerEpoch, setComposerEpoch] = useState(0);
  const collapseTouchedRef = useRef(false);
  const itemsRef = useRef(state.items);
  itemsRef.current = state.items;
  const conversationIdRef = useRef(conversationId);
  const privacyMode = false;

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

  const touch = useCallback((id: string) => {
    const existing = itemsRef.current.find((item) => item.id === id);
    if (!existing || existing.lastMessageAt) {
      return;
    }
    dispatch({
      type: 'UPSERT',
      item: {
        ...existing,
        lastMessageAt: new Date().toISOString(),
      },
    });
  }, []);

  const discardUnusedDrafts = useCallback(async (exceptId?: string | null) => {
    const ids = unusedConversationIds(itemsRef.current, exceptId).filter(
      (id) => !peekConversationDraft(id),
    );
    ids.forEach((id) => {
      dispatch({
        type: 'REMOVE',
        id,
      });
    });
    await Promise.all(
      ids.map(async (id) => {
        try {
          await deleteConversation(id);
        } catch (err: unknown) {
          if (isAuthRequired(err)) {
            throw err;
          }
        }
      }),
    );
  }, []);

  useEffect(() => {
    const previousId = conversationIdRef.current;
    conversationIdRef.current = conversationId;
    if (!previousId || previousId === conversationId) {
      return;
    }
    const left = itemsRef.current.find((item) => item.id === previousId);
    if (left && isUnusedConversation(left)) {
      void discardUnusedDrafts(conversationId ?? null);
    }
  }, [conversationId, discardUnusedDrafts]);

  const handleNew = useCallback(() => {
    setComposerEpoch((epoch) => epoch + 1);
    if (isNewConversationPath(pathname)) {
      return;
    }
    void discardUnusedDrafts();
    navigate('/app');
  }, [discardUnusedDrafts, navigate, pathname]);

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
      if (id === conversationId) {
        return;
      }
      navigate(`/app/chat/${id}`);
    },
    [conversationId, navigate],
  );

  /**
   * The saved default is applied only once preferences are known to be loaded, and never on top of a
   * collapse the user already performed in this session.
   */
  useEffect(() => {
    if (preferencesStatus === 'ready' && !collapseTouchedRef.current) {
      setSidebarCollapsed(preferences.sidebarCollapsed);
    }
  }, [preferences.sidebarCollapsed, preferencesStatus]);

  const toggleSidebar = useCallback(() => {
    collapseTouchedRef.current = true;
    setSidebarCollapsed((prev) => !prev);
  }, []);

  const contextValue = useMemo<ConversationLayoutContextValue>(
    () => ({
      reload,
      upsert,
      remove,
      items: state.items,
      discardUnusedDrafts,
      touch,
      privacyMode,
      composerEpoch,
    }),
    [
      composerEpoch,
      discardUnusedDrafts,
      reload,
      remove,
      state.items,
      touch,
      upsert,
      privacyMode,
    ],
  );

  const layout = (
    <ConversationLayoutContext.Provider value={contextValue}>
      <div className="h-full w-full flex bg-page">
        {sidebarCollapsed ? (
          <div className="h-full w-[52px] shrink-0 flex flex-col items-center gap-4 bg-sidebar border-r border-border py-12">
            <Tooltip title="展开侧边栏" placement="right">
              <button
                type="button"
                onClick={toggleSidebar}
                aria-label="展开侧边栏"
                aria-expanded={false}
                data-testid="sidebar-toggle"
                className="flex h-32 w-32 items-center justify-center rounded-[8px] text-text-secondary hover:bg-[#F5F5F7] transition-colors duration-150"
              >
                <MenuUnfoldOutlined className="text-[16px]" />
              </button>
            </Tooltip>
            <Tooltip title="新会话" placement="right">
              <button
                type="button"
                onClick={handleNew}
                aria-label="新会话"
                className="flex h-32 w-32 items-center justify-center rounded-[8px] text-text-secondary hover:bg-[#F5F5F7] transition-colors duration-150"
              >
                <FormOutlined className="text-[16px]" />
              </button>
            </Tooltip>
          </div>
        ) : (
          <div className="h-full w-[272px] shrink-0 flex flex-col bg-sidebar border-r border-border">
            <div className="w-full px-14 py-12 border-b border-border flex items-center justify-between gap-8">
              <Logo hideSplit />
              <Tooltip title="收起侧边栏">
                <button
                  type="button"
                  onClick={toggleSidebar}
                  aria-label="收起侧边栏"
                  aria-expanded
                  data-testid="sidebar-toggle"
                  className="flex h-24 w-24 items-center justify-center rounded-[6px] text-text-secondary hover:bg-[#F5F5F7] transition-colors duration-150"
                >
                  <MenuFoldOutlined className="text-[14px]" />
                </button>
              </Tooltip>
            </div>
            <div className="w-full px-10 pt-10 pb-8 border-b border-border flex flex-col gap-4">
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
            <AppNavigation />
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
            <SidebarUserMenu />
          </div>
        )}
        <div className="flex-1 min-w-0 h-full overflow-hidden bg-surface">
          {isChatSurfacePath(pathname) ? (
            <Suspense
              fallback={
                <div className="h-full w-full flex items-center justify-center">
                  <Spin />
                </div>
              }
            >
              <ConversationPage />
            </Suspense>
          ) : (
            <div className="h-full min-h-0 [&>*]:h-full">
              <Outlet />
            </div>
          )}
        </div>
      </div>
      <SettingsModal />
    </ConversationLayoutContext.Provider>
  );

  if (user?.id) {
    return (
      <SettingsModalProvider>
        <LocalMemoryProvider userId={user.id}>{layout}</LocalMemoryProvider>
      </SettingsModalProvider>
    );
  }

  return (
    <SettingsModalProvider>
      {layout}
    </SettingsModalProvider>
  );
});

ConversationLayout.displayName = 'ConversationLayout';

export default ConversationLayout;
