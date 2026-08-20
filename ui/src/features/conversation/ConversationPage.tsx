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
import { Button, Result, Spin, message } from 'antd';
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
import { useUserSettings } from '@/features/userSettings/useUserSettings';
import { MvpApiError } from '@/services/apiError';
import {
  createConversation,
  getConversation,
  getMessages,
  updateConversationPrivacy,
} from './api';
import { hydrateConversation } from './hydrateConversation';
import { peekLiveChatRun } from './liveChatRuns';
import { useConversationLayout } from './ConversationLayout';
import {
  clearConversationDraft,
  peekConversationDraft,
} from './pendingConversationDraft';
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
  const { conversationId } = useParams<{ conversationId?: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const layout = useConversationLayout();
  const localMemory = useLocalMemoryOptional();
  const { preferences, status: preferencesStatus } = useUserSettings();

  const [conversation, setConversation] = useState<ConversationResponse | null>(
    null,
  );
  const [rawMessages, setRawMessages] = useState<ConversationMessageResponse[]>(
    [],
  );
  const [chats, setChats] = useState<PersistedChatItem[]>([]);
  const [loading, setLoading] = useState(() => Boolean(conversationId));
  const [historyReady, setHistoryReady] = useState(() => !conversationId);
  const [pendingDraft, setPendingDraft] = useState<ConversationDraft | null>(
    null,
  );
  const [executionMode, setExecutionMode] = useState<ExecutionMode>('AUTO');
  const [allowedAgentIds, setAllowedAgentIds] = useState<string[]>([]);
  const [teamId, setTeamId] = useState<string | null>(null);
  const [composerPrivacy, setComposerPrivacy] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [reloadNonce, setReloadNonce] = useState(0);
  const consumedDraftIdsRef = useRef<Set<string>>(new Set());
  const createdComposerIdRef = useRef<string | null>(null);
  const ensuringPromiseRef = useRef<Promise<string | null> | null>(null);

  const executionSeededForIdRef = useRef<string | null>(null);
  const prevConversationIdRef = useRef<string | undefined>(conversationId);
  const chatViewInstanceRef = useRef(0);
  if (prevConversationIdRef.current !== conversationId) {
    const liveHandoff =
      !prevConversationIdRef.current &&
      Boolean(conversationId) &&
      peekLiveChatRun(conversationId as string)?.sendInFlight === true;
    if (!liveHandoff) {
      chatViewInstanceRef.current += 1;
    }
    prevConversationIdRef.current = conversationId;
  }

  // Send-mode selection is not persisted across refresh; empty chats pick up saved defaults.
  useEffect(() => {
    if (
      conversationId &&
      peekLiveChatRun(conversationId)?.sendInFlight === true
    ) {
      return;
    }
    setExecutionMode('AUTO');
    setAllowedAgentIds([]);
    setTeamId(null);
    executionSeededForIdRef.current = null;
  }, [conversationId]);

  useEffect(() => {
    if (!historyReady || preferencesStatus !== 'ready') {
      return;
    }
    const seedKey = conversationId ?? 'composer';
    if (executionSeededForIdRef.current === seedKey) {
      return;
    }
    if (conversationId && (chats.length > 0 || pendingDraft)) {
      executionSeededForIdRef.current = seedKey;
      return;
    }
    executionSeededForIdRef.current = seedKey;
    setExecutionMode(preferences.defaultExecutionMode);
  }, [
    chats.length,
    conversationId,
    historyReady,
    pendingDraft,
    preferences.defaultExecutionMode,
    preferencesStatus,
  ]);

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
    if (conversationId && chats.length > 0) {
      const latest = chats[chats.length - 1];
      return {
        deepThink: latest.deepThink,
        outputStyle: latest.outputStyle,
        productType: pendingDraft?.productType,
      };
    }
    if (conversationId && pendingDraft) {
      return {
        deepThink: !!pendingDraft.inputInfo.deepThink,
        outputStyle: resolveOutputStyle(pendingDraft.inputInfo.outputStyle),
        productType: pendingDraft.productType,
      };
    }
    return {
      deepThink: false,
      outputStyle: resolveOutputStyle(undefined),
      productType: undefined,
    };
  }, [chats, conversationId, pendingDraft]);

  const layoutRef = useRef(layout);
  layoutRef.current = layout;
  const localMemoryRef = useRef(localMemory);
  localMemoryRef.current = localMemory;
  const conversationRef = useRef(conversation);
  conversationRef.current = conversation;

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
      if (
        list.length === 0 &&
        peekLiveChatRun(conversationId)?.sendInFlight
      ) {
        return;
      }
      await applyMessages(list, conversationId);
    } catch (err: unknown) {
      if (isAuthRequired(err)) {
        throw err;
      }
      if (
        err instanceof MvpApiError &&
        (err.httpStatus === 404 || err.code === 'RESOURCE_NOT_FOUND')
      ) {
        if (peekLiveChatRun(conversationId)?.sendInFlight) {
          return;
        }
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

  const ensureConversation = useCallback(async () => {
    if (conversationId) {
      return conversationId;
    }
    if (createdComposerIdRef.current) {
      return createdComposerIdRef.current;
    }
    if (ensuringPromiseRef.current) {
      return ensuringPromiseRef.current;
    }
    const pending = (async () => {
      try {
        await layout?.discardUnusedDrafts?.();
        const created = await createConversation(null, composerPrivacy);
        if (!created) {
          message.error('创建会话失败');
          return null;
        }
        layout?.upsert({
          ...created,
          lastMessageAt: null,
          lastMessagePreview: null,
        });
        createdComposerIdRef.current = created.id;
        return created.id;
      } catch (err: unknown) {
        if (isAuthRequired(err)) {
          throw err;
        }
        if (err instanceof MvpApiError && err.code === 'ACCESS_DENIED') {
          message.error('无权限创建会话');
          return null;
        }
        message.error(
          err instanceof MvpApiError ? err.message : '创建会话失败',
        );
        return null;
      }
    })();
    ensuringPromiseRef.current = pending;
    try {
      return await pending;
    } finally {
      ensuringPromiseRef.current = null;
    }
  }, [composerPrivacy, conversationId, layout]);

  const prevComposerEpochRef = useRef(layout?.composerEpoch);
  useEffect(() => {
    if (conversationId) {
      createdComposerIdRef.current = conversationId;
      prevComposerEpochRef.current = layout?.composerEpoch;
      return;
    }
    if (prevComposerEpochRef.current !== layout?.composerEpoch) {
      createdComposerIdRef.current = null;
      prevComposerEpochRef.current = layout?.composerEpoch;
    }
  }, [conversationId, layout?.composerEpoch]);

  const togglePrivacyMode = useCallback(async () => {
    if (!conversationId) {
      setComposerPrivacy((prev) => !prev);
      return;
    }
    if (!conversation) {
      return;
    }
    const next = !conversation.privacyMode;
    try {
      const updated = await updateConversationPrivacy(conversationId, next);
      if (!updated) {
        message.error('更新隐私模式失败');
        return;
      }
      setConversation(updated);
      const ctx = layoutRef.current;
      if (ctx) {
        const existing = ctx.items.find((row) => row.id === updated.id);
        ctx.upsert({
          id: updated.id,
          title: updated.title,
          privacyMode: updated.privacyMode === true,
          lastMessageAt: updated.lastMessageAt,
          createdAt: updated.createdAt,
          updatedAt: updated.updatedAt,
          lastMessagePreview: existing?.lastMessagePreview ?? null,
        });
      }
    } catch (err: unknown) {
      if (isAuthRequired(err)) {
        throw err;
      }
      message.error(
        err instanceof MvpApiError ? err.message : '更新隐私模式失败',
      );
    }
  }, [conversation, conversationId]);

  // Load conversation + messages. Keep any first-send draft across remounts.
  useEffect(() => {
    if (!conversationId) {
      setConversation(null);
      setRawMessages([]);
      setChats([]);
      setPendingDraft(null);
      setLoadError(null);
      setLoading(false);
      setHistoryReady(true);
      return;
    }

    let cancelled = false;
    setHistoryReady(false);
    const handedOff =
      peekConversationDraft(conversationId) ??
      (isConversationDraft(location.state) ? location.state : null);
    if (handedOff) {
      setPendingDraft(handedOff);
      if (isPhase2Enabled()) {
        setExecutionMode(handedOff.executionMode ?? 'AUTO');
        setAllowedAgentIds(handedOff.allowedAgentIds ?? []);
        setTeamId(handedOff.teamId ?? null);
      }
    }

    const load = async () => {
      setLoadError(null);
      const current = conversationRef.current;
      if (!current || current.id !== conversationId) {
        setLoading(true);
      }
      try {
        const [conv, messages] = await Promise.all([
          getConversation(conversationId),
          getMessages(conversationId),
        ]);
        if (cancelled) {
          return;
        }
        if (!conv) {
          if (peekLiveChatRun(conversationId)?.sendInFlight) {
            return;
          }
          layoutRef.current?.remove(conversationId);
          navigate('/app');
          return;
        }
        setConversation(conv);
        const list = messages ?? [];
        const keepLocalTurns =
          Boolean(peekLiveChatRun(conversationId)?.sendInFlight) ||
          (list.length === 0 && Boolean(peekConversationDraft(conversationId)));
        if (keepLocalTurns) {
          setRawMessages(list);
        } else {
          await applyMessages(list, conversationId);
        }
        const ctx = layoutRef.current;
        if (ctx) {
          const existing = ctx.items.find((row) => row.id === conv.id);
          ctx.upsert({
            id: conv.id,
            title: conv.title,
            privacyMode: conv.privacyMode === true,
            lastMessageAt: conv.lastMessageAt ?? existing?.lastMessageAt ?? null,
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
          if (peekLiveChatRun(conversationId)?.sendInFlight) {
            return;
          }
          layoutRef.current?.remove(conversationId);
          navigate('/app');
          return;
        }
        if (err instanceof MvpApiError && err.code === 'ACCESS_DENIED') {
          message.error('无权限访问该会话');
          navigate('/app');
          return;
        }
        const reason =
          err instanceof MvpApiError ? err.message : '加载会话失败';
        setLoadError(reason);
        message.error(reason);
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
  }, [applyMessages, conversationId, navigate, reloadNonce]);

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
    const handedOff =
      peekConversationDraft(conversationId) ??
      (isConversationDraft(location.state) ? location.state : null);
    if (!handedOff) {
      return;
    }
    if (consumedDraftIdsRef.current.has(handedOff.requestId)) {
      if (location.state != null) {
        navigate(location.pathname, {
          replace: true,
          state: null
        });
      }
      return;
    }

    consumedDraftIdsRef.current.add(handedOff.requestId);

    if (chats.some((chat) => chat.requestId === handedOff.requestId)) {
      clearConversationDraft(conversationId);
      navigate(location.pathname, {
        replace: true,
        state: null
      });
      return;
    }

    if (isPhase2Enabled()) {
      setExecutionMode(handedOff.executionMode ?? 'AUTO');
      setAllowedAgentIds(handedOff.allowedAgentIds ?? []);
      setTeamId(handedOff.teamId ?? null);
    }
    setPendingDraft(handedOff);
    if (location.state != null) {
      navigate(location.pathname, {
        replace: true,
        state: null
      });
    }
  }, [
    historyReady,
    loading,
    conversationId,
    location.pathname,
    location.state,
    navigate,
    chats,
  ]);

  useEffect(() => {
    if (!pendingDraft || !conversationId) {
      return;
    }
    if (chats.some((chat) => chat.requestId === pendingDraft.requestId)) {
      clearConversationDraft(conversationId);
      setPendingDraft(null);
    }
  }, [chats, conversationId, pendingDraft]);

  const liveHandoff = Boolean(
    conversationId && peekLiveChatRun(conversationId)?.sendInFlight === true,
  );
  const showBootSpinner =
    Boolean(conversationId) &&
    loading &&
    !conversation &&
    !pendingDraft &&
    !liveHandoff;

  if (!loading && loadError && !conversation && !liveHandoff) {
    return (
      <div
        className="h-full w-full flex items-center justify-center"
        data-testid="conversation-load-error"
      >
        <Result
          status="warning"
          title="会话加载失败"
          subTitle={loadError}
          extra={
            <Button
              type="primary"
              onClick={() => setReloadNonce((nonce) => nonce + 1)}
              data-testid="conversation-load-retry"
            >
              重试
            </Button>
          }
        />
      </div>
    );
  }

  if (showBootSpinner) {
    return (
      <div className="h-full w-full flex items-center justify-center">
        <Spin />
      </div>
    );
  }

  // Retained for Phase2 memory observation / future consumers of server message rows.
  void rawMessages;

  return (
    <div
      className="h-full w-full"
      data-testid={conversationId ? undefined : 'new-conversation'}
    >
      <ChatView
        key={`${chatViewInstanceRef.current}-${conversationId ?? `new-${layout?.composerEpoch ?? 0}`}`}
        conversationId={conversationId}
        conversationTitle={conversation?.title ?? '新会话'}
        initialChats={conversationId ? chats : []}
        mode={derivedMode}
        executionMode={executionMode}
        allowedAgentIds={allowedAgentIds}
        teamId={teamId}
        onExecutionModeChange={setExecutionMode}
        onAllowedAgentIdsChange={setAllowedAgentIds}
        onTeamIdChange={setTeamId}
        privacyMode={
          conversation ? conversation.privacyMode === true : composerPrivacy
        }
        onPrivacyModeChange={() => void togglePrivacyMode()}
        initialDraft={
          conversationId && pendingDraft
            ? {
              requestId: pendingDraft.requestId,
              inputInfo: pendingDraft.inputInfo,
            }
            : undefined
        }
        detachedRunning={Boolean(conversationId) && detachedRunning}
        onReloadMessages={onReloadMessages}
        onConversationChanged={refreshConversationMeta}
        onEnsureConversation={conversationId ? undefined : ensureConversation}
      />
    </div>
  );
});

ConversationPage.displayName = 'ConversationPage';

export default ConversationPage;
