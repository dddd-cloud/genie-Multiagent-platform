import {
  memo,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  useLocation,
  useNavigate,
  useParams,
} from 'react-router-dom';
import { Spin, message } from 'antd';
import type {
  ConversationMessageResponse,
  ConversationResponse,
  ExecutionMode,
  OutputStyle,
} from '@/contracts';
import { OUTPUT_STYLES } from '@/contracts';
import ChatView from '@/components/ChatView';
import { isPhase2Enabled } from '@/features/phase2/executionMode/featureFlag';
import { useLocalMemoryOptional } from '@/features/phase2/localMemory/useLocalMemory';
import { MvpApiError } from '@/services/apiError';
import { getConversation, getMessages } from './api';
import { hydrateConversation } from './hydrateConversation';
import { useConversationLayout } from './ConversationLayout';
import type { ConversationDraft, PersistedChatItem } from './types';

function isConversationDraft(value: unknown): value is ConversationDraft {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const draft = value as ConversationDraft;
  return (
    typeof draft.requestId === 'string' &&
    !!draft.inputInfo &&
    typeof draft.inputInfo.message === 'string'
  );
}

function resolveOutputStyle(value: string | null | undefined): OutputStyle {
  if (value && (OUTPUT_STYLES as readonly string[]).includes(value)) {
    return value as OutputStyle;
  }
  return 'docs';
}

function isAuthRequired(err: unknown): err is MvpApiError {
  return err instanceof MvpApiError && err.code === 'AUTH_REQUIRED';
}

const ConversationPage: GenieType.FC = memo(() => {
  const { conversationId } = useParams<{ conversationId: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const layout = useConversationLayout();
  const localMemory = useLocalMemoryOptional();

  const [conversation, setConversation] = useState<ConversationResponse | null>(
    null,
  );
  const [rawMessages, setRawMessages] = useState<ConversationMessageResponse[]>(
    [],
  );
  const [chats, setChats] = useState<PersistedChatItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [historyReady, setHistoryReady] = useState(false);
  const [pendingDraft, setPendingDraft] = useState<ConversationDraft | null>(
    null,
  );
  const [executionMode, setExecutionMode] = useState<ExecutionMode>('AUTO');
  const [allowedAgentIds, setAllowedAgentIds] = useState<string[]>([]);
  const consumedDraftIdsRef = useRef<Set<string>>(new Set());

  // Send-mode selection is not persisted across refresh.
  useEffect(() => {
    setExecutionMode('AUTO');
    setAllowedAgentIds([]);
  }, [conversationId]);

  const detachedRunning = useMemo(
    () =>
      chats.some(
        (chat) =>
          chat.persistedStatus === 'PENDING' ||
          chat.persistedStatus === 'STREAMING',
      ),
    [chats],
  );

  const derivedMode = useMemo(() => {
    if (chats.length > 0) {
      const latest = chats[chats.length - 1];
      return {
        deepThink: latest.deepThink,
        outputStyle: latest.outputStyle,
        productType: pendingDraft?.productType,
      };
    }
    if (pendingDraft) {
      return {
        deepThink: !!pendingDraft.inputInfo.deepThink,
        outputStyle: resolveOutputStyle(pendingDraft.inputInfo.outputStyle),
        productType: pendingDraft.productType,
      };
    }
    return {
      deepThink: false,
      outputStyle: 'docs' as OutputStyle,
    };
  }, [chats, pendingDraft]);

  const layoutRef = useRef(layout);
  layoutRef.current = layout;
  const localMemoryRef = useRef(localMemory);
  localMemoryRef.current = localMemory;

  const applyMessages = useCallback(
    async (list: ConversationMessageResponse[], id: string) => {
      setRawMessages(list);
      setChats(hydrateConversation(list, id));
      if (isPhase2Enabled()) {
        try {
          await localMemoryRef.current?.observeCompletedMessages?.(id, list);
        } catch {
          // Memory observation is best-effort; never block chat hydrate.
        }
      }
    },
    [],
  );

  const onReloadMessages = useCallback(async () => {
    if (!conversationId) {
      return;
    }
    try {
      const messages = await getMessages(conversationId);
      const list = messages ?? [];
      await applyMessages(list, conversationId);
    } catch (err: unknown) {
      if (isAuthRequired(err)) {
        throw err;
      }
      if (
        err instanceof MvpApiError &&
        (err.httpStatus === 404 || err.code === 'RESOURCE_NOT_FOUND')
      ) {
        layoutRef.current?.remove(conversationId);
        navigate('/app');
        return;
      }
      if (err instanceof MvpApiError && err.code === 'ACCESS_DENIED') {
        message.error('无权限访问该会话');
        return;
      }
      message.error(
        err instanceof MvpApiError ? err.message : '刷新消息失败',
      );
    }
  }, [applyMessages, conversationId, navigate]);

  /**
   * Plan §10.3: after SSE open / terminal, reload conversation detail AND list.
   * ConversationResponse has no lastMessagePreview — list reload is the only
   * correct way to refresh sidebar title/preview/lastMessageAt from backend.
   */
  const refreshConversationMeta = useCallback(async () => {
    if (!conversationId) {
      return;
    }
    try {
      const conv = await getConversation(conversationId);
      if (conv) {
        setConversation(conv);
      }
      await layoutRef.current?.reload();
    } catch (err: unknown) {
      if (isAuthRequired(err)) {
        throw err;
      }
      // Title/list refresh is best-effort; do not fail the stream path.
    }
  }, [conversationId]);

  // Load conversation + messages. Draft is NOT consumed here.
  useEffect(() => {
    if (!conversationId) {
      navigate('/app');
      return;
    }

    let cancelled = false;
    setHistoryReady(false);
    setPendingDraft(null);

    const load = async () => {
      setLoading(true);
      try {
        const [conv, messages] = await Promise.all([
          getConversation(conversationId),
          getMessages(conversationId),
        ]);
        if (cancelled) {
          return;
        }
        if (!conv) {
          layoutRef.current?.remove(conversationId);
          navigate('/app');
          return;
        }
        setConversation(conv);
        const list = messages ?? [];
        await applyMessages(list, conversationId);
        const ctx = layoutRef.current;
        if (ctx) {
          const existing = ctx.items.find((row) => row.id === conv.id);
          // Keep existing preview; ConversationResponse has no preview field.
          ctx.upsert({
            id: conv.id,
            title: conv.title,
            lastMessageAt: conv.lastMessageAt,
            createdAt: conv.createdAt,
            updatedAt: conv.updatedAt,
            lastMessagePreview: existing?.lastMessagePreview ?? null,
          });
        }
        setHistoryReady(true);
      } catch (err: unknown) {
        if (cancelled) {
          return;
        }
        if (isAuthRequired(err)) {
          throw err;
        }
        if (
          err instanceof MvpApiError &&
          (err.httpStatus === 404 || err.code === 'RESOURCE_NOT_FOUND')
        ) {
          layoutRef.current?.remove(conversationId);
          navigate('/app');
          return;
        }
        if (err instanceof MvpApiError && err.code === 'ACCESS_DENIED') {
          message.error('无权限访问该会话');
          navigate('/app');
          return;
        }
        message.error(
          err instanceof MvpApiError ? err.message : '加载会话失败',
        );
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    void load();
    return () => {
      cancelled = true;
    };
  }, [applyMessages, conversationId, navigate]);

  /**
   * Plan §9.3 / §9.4:
   * 1) history load finished
   * 2) consume draft once
   * 3) replace route state
   * 4) ChatView mounts with initialDraft and sends with fixed requestId
   * No wall-clock timeout — refresh before SSE loses draft (acceptable); never double-send.
   */
  useEffect(() => {
    if (!historyReady || !conversationId || loading) {
      return;
    }
    const state = location.state;
    if (!isConversationDraft(state)) {
      return;
    }
    if (consumedDraftIdsRef.current.has(state.requestId)) {
      if (location.state != null) {
        navigate(location.pathname, {
          replace: true,
          state: null
        });
      }
      return;
    }

    consumedDraftIdsRef.current.add(state.requestId);

    // Already persisted for this requestId → do not resend; only clear state.
    if (chats.some((chat) => chat.requestId === state.requestId)) {
      navigate(location.pathname, {
        replace: true,
        state: null
      });
      return;
    }

    if (isPhase2Enabled()) {
      setExecutionMode(state.executionMode ?? 'AUTO');
      setAllowedAgentIds(state.allowedAgentIds ?? []);
    }
    setPendingDraft(state);
    navigate(location.pathname, {
      replace: true,
      state: null
    });
  }, [
    historyReady,
    loading,
    conversationId,
    location.pathname,
    location.state,
    navigate,
    chats,
  ]);

  // Clear draft handoff only after the turn appears in hydrated/local chats.
  useEffect(() => {
    if (!pendingDraft) {
      return;
    }
    if (chats.some((chat) => chat.requestId === pendingDraft.requestId)) {
      setPendingDraft(null);
    }
  }, [chats, pendingDraft]);

  if (loading || !conversationId || !conversation) {
    return (
      <div className="h-full w-full flex items-center justify-center">
        <Spin />
      </div>
    );
  }

  // Retained for Phase2 memory observation / future consumers of server message rows.
  void rawMessages;

  return (
    <ChatView
      key={conversationId}
      conversationId={conversationId}
      conversationTitle={conversation.title}
      initialChats={chats}
      mode={derivedMode}
      executionMode={executionMode}
      allowedAgentIds={allowedAgentIds}
      onExecutionModeChange={setExecutionMode}
      onAllowedAgentIdsChange={setAllowedAgentIds}
      initialDraft={
        pendingDraft
          ? {
            requestId: pendingDraft.requestId,
            inputInfo: pendingDraft.inputInfo,
          }
          : undefined
      }
      detachedRunning={detachedRunning}
      onReloadMessages={onReloadMessages}
      onConversationChanged={refreshConversationMeta}
    />
  );
});

ConversationPage.displayName = 'ConversationPage';

export default ConversationPage;
