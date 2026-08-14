import { useEffect, useMemo, useRef, useState, type WheelEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Modal, message } from 'antd';
import classNames from 'classnames';
import { useMemoizedFn } from 'ahooks';
import type { ExecutionMode, OutputStyle } from '@/contracts';
import { OUTPUT_STYLES } from '@/contracts';
import { notifyMvpError } from '@/features/auth/mvpErrorBus';
import { useConversationLayout } from '@/features/conversation/ConversationLayout';
import type { PersistedChatItem } from '@/features/conversation/types';
import { isUuid } from '@/features/conversation/requestId';
import {
  beginLiveChatRun,
  finishLiveChatRun,
  patchLiveChatRun,
  peekLiveChatRun,
  stopLiveChatRun,
  subscribeLiveChatRun,
} from '@/features/conversation/liveChatRuns';
import ExecutionModeSelector from '@/features/phase2/executionMode/ExecutionModeSelector';
import { isPhase2Enabled } from '@/features/phase2/executionMode/featureFlag';
import { buildPhase2GptQueryRequest } from '@/features/phase2/executionMode/phase2RequestBuilder';
import { useLocalMemoryOptional } from '@/features/phase2/localMemory/useLocalMemory';
import OrchestrationTimeline from '@/features/phase2/orchestration/OrchestrationTimeline';
import {
  createInitialOrchestrationState,
  markOrchestrationDone,
  preserveOrchestrationFold,
  reduceOrchestrationEvent,
  reduceOrchestrationTrace,
  toggleMainOpen,
  toggleMasterOpen,
  toggleStepOpen,
  toggleSubTaskOpen,
} from '@/features/phase2/orchestration/orchestrationReducer';
import { extractOrchestrationEventFromResult } from '@/features/phase2/orchestration/parseOrchestrationEvent';
import { extractOrchestrationTraceFromResult } from '@/features/phase2/orchestration/parseOrchestrationTrace';
import {
  getSharedBrowserSkillExecutionRunner,
} from '@/features/phase2/skillRuntime/BrowserSkillExecutionRunner';
import { extractBrowserSkillSignalFromResult } from '@/features/phase2/skillRuntime/signal';
import { MvpApiError } from '@/services/apiError';
import { getPhase2ErrorMessage } from '@/services/phase2/errorMessages';
import { queryPhase2SSE } from '@/services/phase2/queryPhase2SSE';
import {
  ActionViewItemEnum,
  scrollToTop,
} from '@/utils';
import { RESULT_TYPES, productList } from '@/utils/constants';
import { combineData, extractGeneratedFiles, handleTaskData } from '@/utils/chat';
import querySSE, {
  type SseHandle,
  type SseTerminalResult,
} from '@/utils/querySSE';
import Dialogue from '@/components/Dialogue';
import GeneralInput from '@/components/GeneralInput';
import ActionView from '@/components/ActionView';
import Logo from '@/components/Logo';

interface ChatViewProps {
  conversationId: string;
  conversationTitle: string;
  initialChats: PersistedChatItem[];
  mode: {
    deepThink: boolean;
    outputStyle: OutputStyle;
    productType?: string;
  };
  initialDraft?: {
    requestId: string;
    inputInfo: CHAT.TInputInfo;
  };
  detachedRunning: boolean;
  onReloadMessages: () => Promise<void>;
  onConversationChanged: () => Promise<void>;
  executionMode?: ExecutionMode;
  allowedAgentIds?: string[];
  onExecutionModeChange?: (mode: ExecutionMode) => void;
  onAllowedAgentIdsChange?: (agentIds: string[]) => void;
}

const QUERY_MIN = 1;
const QUERY_MAX = 20000;
const RECONCILE_GAP_MS = 1500;
const RECONCILE_FLUSH_MS = 50;

/** Survives React Strict Mode remounts so a draft requestId is only sent once. */
const consumedDraftRequestIds = new Set<string>();

function resolveOutputStyle(value: string | null | undefined): OutputStyle {
  if (value && (OUTPUT_STYLES as readonly string[]).includes(value)) {
    return value as OutputStyle;
  }
  return 'docs';
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

function isNonTerminalStatus(status: PersistedChatItem['persistedStatus']): boolean {
  return status === 'PENDING' || status === 'STREAMING';
}

function stillDetachedRunning(chats: PersistedChatItem[]): boolean {
  return chats.some((chat) => isNonTerminalStatus(chat.persistedStatus));
}

/** Mutable working copy for combineData without mutating React state trees. */
function cloneWorkingChat(item: PersistedChatItem): PersistedChatItem {
  return {
    ...item,
    multiAgent: structuredClone(item.multiAgent ?? { tasks: [] }),
    tasks: item.tasks ? [...item.tasks] : [],
    files: item.files ? [...item.files] : [],
    orchestration: item.orchestration
      ? structuredClone(item.orchestration)
      : undefined,
  };
}

function isMalformedStreamMessage(msg?: string): boolean {
  if (!msg) {
    return false;
  }
  return (
    msg.includes('Failed to parse SSE') ||
    msg.includes('SSE message is not an object')
  );
}

function buildLoadingChat(
  inputInfo: CHAT.TInputInfo,
  sessionId: string,
  requestId: string,
  deepThink: boolean,
  outputStyle: OutputStyle,
): PersistedChatItem {
  return {
    query: inputInfo.message,
    files: inputInfo.files ?? [],
    responseType: 'txt',
    sessionId,
    requestId,
    loading: true,
    forceStop: false,
    tasks: [],
    thought: '',
    response: '',
    taskStatus: 0,
    tip: '已接收到你的任务，将立即开始处理...',
    multiAgent: { tasks: [] },
    deepThink,
    outputStyle,
    persistedStatus: 'STREAMING',
  };
}

type LocalContextChoice =
  | { action: 'abort' }
  | { action: 'continue'; longTermMemory: string; conversationSummary: string };

async function resolvePhase2LocalContext(
  conversationId: string,
  localMemory: ReturnType<typeof useLocalMemoryOptional>,
  navigate: (path: string) => void,
): Promise<LocalContextChoice> {
  if (!localMemory?.repository) {
    return {
      action: 'continue',
      longTermMemory: '',
      conversationSummary: ''
    };
  }

  let ltmRaw = '';
  let summaryRaw = '';
  let corrupted = false;
  let unavailable = false;

  try {
    const [ltm, summary] = await Promise.all([
      localMemory.repository.readLongTermMemory(),
      localMemory.repository.readConversationSummary(conversationId),
    ]);

    if (ltm.status === 'UNAVAILABLE' || summary.status === 'UNAVAILABLE') {
      unavailable = true;
    }
    if (ltm.status === 'CORRUPTED' || summary.status === 'CORRUPTED') {
      corrupted = true;
    }
    if (ltm.status === 'READY' && typeof ltm.raw === 'string') {
      ltmRaw = ltm.raw;
    }
    if (summary.status === 'READY' && typeof summary.raw === 'string') {
      summaryRaw = summary.raw;
    }
    if (ltm.status === 'ERROR' || summary.status === 'ERROR') {
      unavailable = true;
    }
  } catch {
    unavailable = true;
  }

  if (corrupted) {
    const choice = await new Promise<'cancel' | 'navigate' | 'continue'>((resolve) => {
      let settled = false;
      const settle = (value: 'cancel' | 'navigate' | 'continue') => {
        if (settled) {
          return;
        }
        settled = true;
        resolve(value);
      };
      Modal.confirm({
        title: '本地记忆文件已损坏',
        content:
          '检测到长期记忆或会话摘要无法解析。不会静默发送损坏内容。请选择下一步。',
        okText: '不带损坏上下文继续',
        cancelText: '取消',
        centered: true,
        footer: (_, { OkBtn, CancelBtn }) => (
          <>
            <CancelBtn />
            <Button
              onClick={() => {
                settle('navigate');
                Modal.destroyAll();
              }}
            >
              前往本地记忆页面
            </Button>
            <OkBtn />
          </>
        ),
        onOk: () => {
          settle('continue');
        },
        onCancel: () => {
          settle('cancel');
        },
      });
    });

    if (choice === 'cancel') {
      return { action: 'abort' };
    }
    if (choice === 'navigate') {
      navigate('/app/settings/memory');
      return { action: 'abort' };
    }
    return {
      action: 'continue',
      longTermMemory: '',
      conversationSummary: ''
    };
  }

  if (unavailable) {
    message.info('本地记忆暂不可用，将不带本地上下文继续');
    return {
      action: 'continue',
      longTermMemory: '',
      conversationSummary: ''
    };
  }

  return {
    action: 'continue',
    longTermMemory: ltmRaw,
    conversationSummary: summaryRaw,
  };
}

function canConsumeWheel(
  start: EventTarget | null,
  root: HTMLElement,
  deltaY: number,
): boolean {
  let node = start instanceof HTMLElement ? start : null;
  while (node && node !== root) {
    const overflowY = window.getComputedStyle(node).overflowY;
    const scrollable =
      (overflowY === 'auto' || overflowY === 'scroll') &&
      node.scrollHeight - node.clientHeight > 1;
    if (scrollable) {
      if (deltaY < 0 && node.scrollTop > 0) {
        return true;
      }
      if (
        deltaY > 0 &&
        node.scrollTop + node.clientHeight < node.scrollHeight - 1
      ) {
        return true;
      }
    }
    node = node.parentElement;
  }
  return false;
}

const ChatView: GenieType.FC<ChatViewProps> = (props) => {
  const {
    conversationId,
    conversationTitle,
    initialChats,
    mode,
    initialDraft,
    detachedRunning,
    onReloadMessages,
    onConversationChanged,
    executionMode = 'AUTO',
    allowedAgentIds = [],
    onExecutionModeChange,
    onAllowedAgentIdsChange,
  } = props;

  const sessionId = conversationId;
  const navigate = useNavigate();
  const layout = useConversationLayout();
  const localMemory = useLocalMemoryOptional();
  const phase2 = isPhase2Enabled();
  const liveOnMount = peekLiveChatRun(sessionId);

  const [chatList, setChatList] = useState<PersistedChatItem[]>(
    liveOnMount?.sendInFlight ? liveOnMount.chatList : initialChats,
  );
  const [taskList, setTaskList] = useState<MESSAGE.Task[]>([]);
  const [activeTask, setActiveTask] = useState<CHAT.Task>();
  const [plan, setPlan] = useState<CHAT.Plan>();
  const [showAction, setShowAction] = useState(false);
  const [sendInFlight, setSendInFlight] = useState(!!liveOnMount?.sendInFlight);
  const [reconcileHint, setReconcileHint] = useState<string | null>(null);
  const [needManualRefresh, setNeedManualRefresh] = useState(false);
  const [reconciling, setReconciling] = useState(false);

  const chatRef = useRef<HTMLDivElement>(null);
  const actionViewRef = ActionView.useActionView();
  const chatListRef = useRef<PersistedChatItem[]>(initialChats);
  chatListRef.current = chatList;

  const relayWheelToChat = useMemoizedFn((event: WheelEvent<HTMLElement>) => {
    const chat = chatRef.current;
    if (!chat || event.ctrlKey || event.metaKey) {
      return;
    }
    if (chat.contains(event.target as Node)) {
      return;
    }
    if (canConsumeWheel(event.target, event.currentTarget, event.deltaY)) {
      return;
    }
    chat.scrollTop += event.deltaY;
  });

  const sseHandleRef = useRef<SseHandle | null>(null);
  const skillExecutionAbortRef = useRef<AbortController | null>(null);
  const sendInFlightRef = useRef(!!liveOnMount?.sendInFlight);
  const openedOnceRef = useRef(false);
  const titleRefreshTimersRef = useRef<number[]>([]);
  const clearTitleRefreshTimers = () => {
    for (const id of titleRefreshTimersRef.current) {
      window.clearTimeout(id);
    }
    titleRefreshTimersRef.current = [];
  };
  const scheduleTitleRefresh = () => {
    clearTitleRefreshTimers();
    for (const delayMs of [2000, 8000]) {
      const id = window.setTimeout(() => {
        titleRefreshTimersRef.current = titleRefreshTimersRef.current.filter((item) => item !== id);
        void onConversationChanged();
      }, delayMs);
      titleRefreshTimersRef.current.push(id);
    }
  };
  const reconcileStartedRef = useRef(false);
  const mountedRef = useRef(true);
  const initialChatsRef = useRef(initialChats);
  initialChatsRef.current = initialChats;
  const executionModeRef = useRef(executionMode);
  executionModeRef.current = executionMode;
  const allowedAgentIdsRef = useRef(allowedAgentIds);
  allowedAgentIdsRef.current = allowedAgentIds;

  const product = useMemo(() => {
    if (!mode.productType) {
      return undefined;
    }
    return productList.find((item) => item.type === mode.productType);
  }, [mode.productType]);

  const sendMode = useMemo(() => {
    if (chatList.length > 0) {
      const latest = chatList[chatList.length - 1];
      return {
        deepThink: latest.deepThink,
        outputStyle: latest.outputStyle,
      };
    }
    return {
      deepThink: mode.deepThink,
      outputStyle: mode.outputStyle,
    };
  }, [chatList, mode.deepThink, mode.outputStyle]);

  const inputDisabled = reconciling;
  const taskRunning = sendInFlight;

  const publishChatList = useMemoizedFn(
    (updater: (prev: PersistedChatItem[]) => PersistedChatItem[]) => {
      const prev = peekLiveChatRun(sessionId)?.chatList ?? chatListRef.current;
      const next = updater(prev);
      chatListRef.current = next;
      patchLiveChatRun(sessionId, { chatList: next });
      if (mountedRef.current) {
        setChatList(next);
      }
      return next;
    },
  );

  const stopGeneration = useMemoizedFn(() => {
    stopLiveChatRun(sessionId);
  });

  useEffect(() => {
    const existing = peekLiveChatRun(sessionId);
    if (existing?.sendInFlight) {
      setChatList(existing.chatList);
      chatListRef.current = existing.chatList;
      sendInFlightRef.current = existing.sendInFlight;
      setSendInFlight(existing.sendInFlight);
    }
    return subscribeLiveChatRun(sessionId, (snapshot) => {
      chatListRef.current = snapshot.chatList;
      setChatList(snapshot.chatList);
      sendInFlightRef.current = snapshot.sendInFlight;
      setSendInFlight(snapshot.sendInFlight);
    });
  }, [sessionId]);

  useEffect(() => {
    if (peekLiveChatRun(sessionId)?.sendInFlight || sendInFlightRef.current) {
      return;
    }
    setChatList(initialChats);
  }, [initialChats, sessionId]);

  const temporaryChangeTask = useMemoizedFn((tasks: MESSAGE.Task[]) => {
    const task = tasks[tasks.length - 1] as CHAT.Task;
    if (!['task_summary', 'result'].includes(task?.messageType)) {
      setActiveTask(task);
    }
  });

  const openAction = useMemoizedFn((tasks: MESSAGE.Task[]) => {
    if (tasks.filter((t) => !RESULT_TYPES.includes(t.messageType)).length) {
      setShowAction(true);
    }
  });

  const updatePlan = useMemoizedFn((next: CHAT.Plan) => {
    setPlan(next);
  });

  const changeActionStatus = useMemoizedFn((status: boolean) => {
    setShowAction(status);
  });

  const changeTask = useMemoizedFn((task: CHAT.Task) => {
    actionViewRef.current?.changeActionView(ActionViewItemEnum.follow);
    changeActionStatus(true);
    setActiveTask(task);
  });

  const changeFile = useMemoizedFn((file: CHAT.TFile) => {
    changeActionStatus(true);
    actionViewRef.current?.setFilePreview(file);
  });

  const changePlan = useMemoizedFn(() => {
    changeActionStatus(true);
    actionViewRef.current?.openPlanView();
  });

  const patchOrchestration = useMemoizedFn(
    (
      requestId: string,
      updater: (
        state: NonNullable<PersistedChatItem['orchestration']>,
      ) => NonNullable<PersistedChatItem['orchestration']>,
    ) => {
      publishChatList((prev) =>
        prev.map((item) => {
          if (item.requestId !== requestId || !item.orchestration) {
            return item;
          }
          return {
            ...item,
            orchestration: updater(item.orchestration),
          };
        }),
      );
    },
  );

  const stopLoadingForRequest = useMemoizedFn((requestId: string, patch?: Partial<PersistedChatItem>) => {
    publishChatList((prev) =>
      prev.map((item) =>
        item.requestId === requestId
          ? {
            ...item,
            loading: false,
            orchestration: item.orchestration
              ? markOrchestrationDone(item.orchestration)
              : item.orchestration,
            ...patch,
          }
          : item,
      ),
    );
  });

  const runLimitedReconcile = useMemoizedFn(async (showDisconnectHint: boolean) => {
    if (reconciling) {
      return;
    }
    setReconciling(true);
    setNeedManualRefresh(false);
    if (showDisconnectHint) {
      setReconcileHint('连接已断开，正在确认状态');
      message.info('连接已断开，正在确认状态');
    } else {
      setReconcileHint('正在确认执行状态…');
    }

    try {
      for (let i = 0; i < 3; i += 1) {
        await onReloadMessages();
        await sleep(RECONCILE_FLUSH_MS);
        const latest = initialChatsRef.current;
        setChatList(latest);
        if (!stillDetachedRunning(latest)) {
          setReconcileHint(null);
          setNeedManualRefresh(false);
          return;
        }
        if (i < 2) {
          await sleep(RECONCILE_GAP_MS);
        }
      }
      setReconcileHint('任务可能仍在其他页面执行');
      setNeedManualRefresh(true);
    } finally {
      setReconciling(false);
    }
  });

  const handleHttpError = useMemoizedFn(async (result: Extract<SseTerminalResult, { kind: 'HTTP_ERROR' }>) => {
    const { httpStatus, code, message: msg } = result;

    // Plan §7.5 / §11.3: only HTTP 401 + AUTH_REQUIRED means session expired.
    if (httpStatus === 401 && code === 'AUTH_REQUIRED') {
      notifyMvpError(
        new MvpApiError(401, 'AUTH_REQUIRED', msg || '未登录或登录已过期'),
      );
      return;
    }

    if (code === 'CSRF_INVALID') {
      notifyMvpError(
        new MvpApiError(
          httpStatus || 403,
          'CSRF_INVALID',
          msg || 'CSRF 校验失败，请重试',
        ),
      );
      // Drop optimistic local turn; backend never accepted the POST.
      await onReloadMessages();
      return;
    }

    if (code === 'ACCESS_DENIED') {
      message.error(msg || '无权限执行该操作');
      await onReloadMessages();
      return;
    }

    if (
      httpStatus === 409 ||
      code === 'CONVERSATION_BUSY' ||
      code === 'DUPLICATE_REQUEST'
    ) {
      message.warning(msg || '当前会话有任务正在执行');
      await onReloadMessages();
      return;
    }

    message.error(msg || `请求失败 (${httpStatus})`);
    await onReloadMessages();
  });

  const handlePhase2HttpError = useMemoizedFn(async (
    result: Extract<SseTerminalResult, { kind: 'HTTP_ERROR' }>,
  ) => {
    const { code, message: msg } = result;

    if (code === 'LOCAL_CONTEXT_INVALID' || code === 'LOCAL_CONTEXT_TOO_LARGE') {
      message.error(getPhase2ErrorMessage(code, msg));
      await onReloadMessages();
      return;
    }

    if (code === 'NO_SUITABLE_AGENT') {
      // Do not auto-switch to DIRECT.
      message.error(getPhase2ErrorMessage(code, msg));
      await onReloadMessages();
      return;
    }

    await handleHttpError(result);
  });

  const sendMessage = useMemoizedFn(
    async (inputInfo: CHAT.TInputInfo, requestIdArg?: string) => {
      if (sendInFlightRef.current) {
        return;
      }

      const query = (inputInfo.message ?? '').trim();
      if (query.length < QUERY_MIN || query.length > QUERY_MAX) {
        message.error(`请输入 ${QUERY_MIN}～${QUERY_MAX} 个字符的问题`);
        return;
      }

      const deepThink = !!inputInfo.deepThink;
      const outputStyle = resolveOutputStyle(inputInfo.outputStyle);
      const requestId = requestIdArg ?? crypto.randomUUID();
      if (!isUuid(requestId)) {
        message.error('requestId 必须是 36 位 UUID');
        return;
      }

      const usePhase2 = isPhase2Enabled();
      let phase2Body: Record<string, unknown> | null = null;

      if (usePhase2) {
        const localCtx = await resolvePhase2LocalContext(
          sessionId,
          localMemory,
          navigate,
        );
        if (localCtx.action === 'abort') {
          return;
        }

        const built = buildPhase2GptQueryRequest({
          sessionId,
          requestId,
          query,
          executionMode: executionModeRef.current,
          deepThink: deepThink ? 1 : 0,
          outputStyle,
          allowedAgentIds: allowedAgentIdsRef.current,
          longTermMemory: localCtx.longTermMemory,
          conversationSummary: localCtx.conversationSummary,
        });

        if (!built.ok) {
          message.error(built.message || '请求参数无效');
          // Never fall back to V1 (especially ORCHESTRATED).
          return;
        }
        phase2Body = built.request as unknown as Record<string, unknown>;
      }

      const loadingChat = buildLoadingChat(
        {
          ...inputInfo,
          message: query
        },
        sessionId,
        requestId,
        deepThink,
        outputStyle,
      );

      let currentChat: PersistedChatItem = loadingChat;
      beginLiveChatRun(sessionId, requestId, [...chatListRef.current, loadingChat]);
      sendInFlightRef.current = true;
      setSendInFlight(true);
      openedOnceRef.current = false;
      clearTitleRefreshTimers();
      layout?.touch(sessionId);

      try {

      const body: Record<string, unknown> = usePhase2 && phase2Body
        ? phase2Body
        : {
          sessionId,
          requestId,
          query,
          deepThink: deepThink ? 1 : 0,
          outputStyle,
        };

      const commitWorkingChat = (working: PersistedChatItem) => {
        publishChatList((prev) => {
          const next = [...prev];
          const idx = next.findIndex((c) => c.requestId === requestId);
          if (idx < 0) {
            return prev;
          }
          const existing = next[idx];
          // User fold toggles update React state; SSE still clones a local
          // currentChat that may lag — keep fold flags from the latest list item.
          const nextItem: PersistedChatItem = {
            ...working,
            orchestration:
              working.orchestration && existing.orchestration
                ? preserveOrchestrationFold(
                    working.orchestration,
                    existing.orchestration,
                  )
                : working.orchestration,
          };
          currentChat = nextItem;
          next[idx] = nextItem;
          return next;
        });
        if (chatRef.current) {
          scrollToTop(chatRef.current);
        }
      };

      const handleMessageV1 = (data: MESSAGE.Answer) => {
        if (peekLiveChatRun(sessionId)?.userStopped) {
          return;
        }
        const { finished, resultMap } = data;
        // Plan §11.4: apply in receive order, sync — no rAF before settle/reload.
        const working = cloneWorkingChat(currentChat);

        if (
          resultMap?.eventData &&
          typeof resultMap.eventData === 'object' &&
          !Array.isArray(resultMap.eventData)
        ) {
          combineData(resultMap.eventData, working);
        }

        if (data.responseAll) {
          working.response = data.responseAll;
        } else if (data.response) {
          working.response = data.response;
        }
        if (finished) {
          const generated = extractGeneratedFiles(resultMap);
          if (generated.length) {
            working.generatedFiles = generated;
          }
          working.loading = false;
        }

        const taskData = handleTaskData(working, deepThink, working.multiAgent);
        setTaskList(taskData.taskList);
        temporaryChangeTask(taskData.taskList);
        if (taskData.plan) {
          updatePlan(taskData.plan);
        }
        openAction(taskData.taskList);
        commitWorkingChat(working);
      };

      const handleMessagePhase2 = (data: MESSAGE.Answer) => {
        if (peekLiveChatRun(sessionId)?.userStopped) {
          return;
        }
        const { finished, resultMap, packageType } = data;

        // skill_execution is a control packet — never chat body / orch terminal.
        if (packageType === 'skill_execution') {
          const signal = extractBrowserSkillSignalFromResult(data);
          if (signal) {
            if (!skillExecutionAbortRef.current) {
              skillExecutionAbortRef.current = new AbortController();
              patchLiveChatRun(sessionId, {
                skillAbort: skillExecutionAbortRef.current,
              });
            }
            void getSharedBrowserSkillExecutionRunner().handleLiveSignal(
              signal,
              skillExecutionAbortRef.current.signal,
            );
          }
          return;
        }

        const orchEvent = extractOrchestrationEventFromResult(data);
        const orchTrace = extractOrchestrationTraceFromResult(data);
        const isOrchPackage =
          packageType === 'orchestration' ||
          packageType === 'orchestration_trace';

        // Reduce inside publishChatList so orchestration always starts from the
        // latest React item (avoids stale currentChat wiping plan objectives).
        publishChatList((prev) => {
          const idx = prev.findIndex((c) => c.requestId === requestId);
          const base =
            idx >= 0 ? cloneWorkingChat(prev[idx]) : cloneWorkingChat(currentChat);
          const working: PersistedChatItem = {
            ...base,
            multiAgent: structuredClone(
              currentChat.multiAgent ?? base.multiAgent ?? { tasks: [] },
            ),
            tasks: currentChat.tasks ? [...currentChat.tasks] : base.tasks,
            files: currentChat.files ? [...currentChat.files] : base.files,
            response: currentChat.response ?? base.response,
            loading: currentChat.loading,
          };

          if (orchEvent) {
            const prevOrch =
              working.orchestration ?? createInitialOrchestrationState();
            working.orchestration = reduceOrchestrationEvent(prevOrch, orchEvent);
            if (working.orchestration.recoveryWarnings.length > 0) {
              working.orchestrationRecoveryWarning = true;
            }
          }

          if (orchTrace) {
            const prevOrch =
              working.orchestration ?? createInitialOrchestrationState();
            working.orchestration = reduceOrchestrationTrace(prevOrch, orchTrace);
          }

          if (!isOrchPackage) {
            if (
              resultMap?.eventData &&
              typeof resultMap.eventData === 'object' &&
              !Array.isArray(resultMap.eventData)
            ) {
              combineData(resultMap.eventData, working);
            }

            if (packageType === 'result' && finished) {
              if (data.responseAll) {
                working.response = data.responseAll;
              } else if (data.response) {
                working.response = data.response;
              }
              const generated = extractGeneratedFiles(resultMap);
              if (generated.length) {
                working.generatedFiles = generated;
              }
              working.loading = false;
              if (working.orchestration) {
                working.orchestration = markOrchestrationDone(
                  working.orchestration,
                );
              }
            } else if (!orchEvent) {
              if (data.responseAll) {
                working.response = data.responseAll;
              } else if (data.response) {
                working.response = data.response;
              }
              if (finished) {
                working.loading = false;
                if (working.orchestration) {
                  working.orchestration = markOrchestrationDone(
                    working.orchestration,
                  );
                }
              }
            } else if (finished) {
              working.loading = false;
              if (working.orchestration) {
                working.orchestration = markOrchestrationDone(
                  working.orchestration,
                );
              }
            }
          }

          const existing = idx >= 0 ? prev[idx] : undefined;
          const nextItem: PersistedChatItem = {
            ...working,
            orchestration:
              working.orchestration && existing?.orchestration
                ? preserveOrchestrationFold(
                    working.orchestration,
                    existing.orchestration,
                  )
                : working.orchestration,
          };
          currentChat = nextItem;
          if (idx < 0) {
            return prev;
          }
          const next = [...prev];
          next[idx] = nextItem;
          return next;
        });

        if (!isOrchPackage) {
          const taskData = handleTaskData(
            currentChat,
            deepThink,
            currentChat.multiAgent,
          );
          setTaskList(taskData.taskList);
          temporaryChangeTask(taskData.taskList);
          if (taskData.plan) {
            updatePlan(taskData.plan);
          }
          openAction(taskData.taskList);
        }

        if (chatRef.current) {
          scrollToTop(chatRef.current);
        }
      };

      const handleMessage = usePhase2 ? handleMessagePhase2 : handleMessageV1;

      const refreshTitleAfterFirstOpen = () => {
        if (openedOnceRef.current) {
          return;
        }
        openedOnceRef.current = true;
        void onConversationChanged();
        scheduleTitleRefresh();
      };

      // Phase2 always uses V2 SSE; never silently fall back to V1 on open failure.
      const handle = usePhase2
        ? queryPhase2SSE({
          body,
          handleMessage,
          onOpen: refreshTitleAfterFirstOpen,
        })
        : querySSE({
          body,
          handleMessage,
          onOpen: refreshTitleAfterFirstOpen,
        });
      sseHandleRef.current = handle;
      patchLiveChatRun(sessionId, { handle });

      const result = await handle.done;
      const stoppedByUser = peekLiveChatRun(sessionId)?.userStopped === true;
      sseHandleRef.current = null;
      // Chat COMPLETED must not abort an in-flight POST result.
      // Cancel Worker only on disconnect / HTTP error / user abort / unmount.
      if (result.kind !== 'COMPLETED') {
        skillExecutionAbortRef.current?.abort();
        skillExecutionAbortRef.current = null;
      }

      if (result.kind === 'COMPLETED' || result.kind === 'FAILED') {
        stopLoadingForRequest(
          requestId,
          result.kind === 'FAILED'
            ? {
              persistedStatus: 'FAILED',
              errorMessage: result.errorMsg ?? '执行失败',
            }
            : { persistedStatus: 'COMPLETED' },
        );
        sendInFlightRef.current = false;
        setSendInFlight(false);
        finishLiveChatRun(sessionId, requestId);
        if (mountedRef.current) {
          await onReloadMessages();
          await onConversationChanged();
        }
        return;
      }

      if (result.kind === 'HTTP_ERROR') {
        // Stop local loading only — do not invent FAILED; reload (except AUTH)
        // clears optimistic turns the backend never accepted.
        // Do NOT auto-resend as V1 after Phase2 POST open failure.
        stopLoadingForRequest(requestId, { tip: '' });
        sendInFlightRef.current = false;
        setSendInFlight(false);
        finishLiveChatRun(sessionId, requestId);
        if (usePhase2) {
          await handlePhase2HttpError(result);
        } else {
          await handleHttpError(result);
        }
        return;
      }

      // INTERRUPTED — plan §11.9/§11.10: never invent DB INTERRUPTED locally;
      // show disconnect hint and reconcile from backend.
      const malformed = isMalformedStreamMessage(result.message);
      if (!stoppedByUser) {
        stopLoadingForRequest(requestId, { tip: '' });
      }
      sendInFlightRef.current = false;
      setSendInFlight(false);
      finishLiveChatRun(sessionId, requestId);

      if (result.reason === 'ABORT') {
        if (stoppedByUser) {
          if (mountedRef.current) {
            await onConversationChanged();
          }
          return;
        }
        // Unmount no longer aborts. Remaining ABORT is logout / explicit stop
        // races / AUTH. Reconcile only if this view is still mounted.
        if (mountedRef.current) {
          await runLimitedReconcile(true);
        }
        return;
      }

      if (malformed) {
        message.error('流式响应格式错误');
        if (mountedRef.current) {
          await onReloadMessages();
        }
        return;
      }

      if (mountedRef.current) {
        await runLimitedReconcile(true);
      }
      } finally {
        finishLiveChatRun(sessionId, requestId);
        sendInFlightRef.current = false;
        if (mountedRef.current) {
          setSendInFlight(false);
        }
      }
    },
  );

  // Consume initialDraft once (module Set survives Strict Mode remount)
  useEffect(() => {
    if (!initialDraft) {
      return;
    }
    if (consumedDraftRequestIds.has(initialDraft.requestId)) {
      return;
    }
    consumedDraftRequestIds.add(initialDraft.requestId);
    void sendMessage(initialDraft.inputInfo, initialDraft.requestId);
  }, [initialDraft, sendMessage]);

  // Detached PENDING/STREAMING on load without local SSE
  useEffect(() => {
    if (reconcileStartedRef.current) {
      return;
    }
    if (initialDraft) {
      return;
    }
    if (peekLiveChatRun(sessionId)?.sendInFlight) {
      return;
    }
    if (!detachedRunning) {
      return;
    }
    reconcileStartedRef.current = true;
    void runLimitedReconcile(false);
  }, [detachedRunning, initialDraft, runLimitedReconcile, sessionId]);

  // Keep the SSE alive across Agent/Skill/other-conversation navigation.
  // Only the stop button (and logout via abortAllActiveSse) may abort.
  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      clearTitleRefreshTimers();
    };
  }, []);

  return (
    <div className="h-full w-full flex bg-surface" onWheel={relayWheelToChat}>
      <div
        className="flex h-full min-w-0 flex-1 flex-col"
        id="chat-view"
      >
        <div className="shrink-0 border-b border-border">
          <div className="mx-auto flex w-full max-w-[960px] items-center justify-between px-24 py-16">
            <div className="flex min-w-0 w-full items-center">
              <Logo />
              <div className="mr-8 overflow-hidden whitespace-nowrap text-ellipsis text-[16px] font-medium text-text-primary">
                {conversationTitle}
              </div>
              {sendMode.deepThink ? (
                <div className="flex shrink-0 items-center rounded-sm border border-border bg-surface-subtle px-8 py-2 text-[12px] text-text-secondary">
                  <i className="font_family icon-shendusikao mr-6 text-[12px]"></i>
                  <span className="ml-[-4px]">深度研究</span>
                </div>
              ) : null}
            </div>
          </div>
        </div>

        {reconcileHint ? (
          <div className="mx-auto w-full max-w-[960px] px-24 pt-12">
            <div className="flex items-center gap-12 rounded-md border border-border bg-surface-subtle px-12 py-8 text-[13px] text-text-secondary">
              <span>{reconcileHint}</span>
              {needManualRefresh ? (
                <button
                  type="button"
                  className="text-brand hover:text-brand-hover transition-colors duration-150"
                  onClick={() => {
                    setNeedManualRefresh(false);
                    void (async () => {
                      await onReloadMessages();
                      setReconcileHint(null);
                    })();
                  }}
                >
                  手动刷新
                </button>
              ) : null}
            </div>
          </div>
        ) : null}

        <div
          className="chat-scroll min-h-0 w-full flex-1 overflow-y-auto"
          ref={chatRef}
        >
          <div className="mx-auto w-full max-w-[960px] px-24 py-16">
          {chatList.map((chat) => {
            const showErrorCard =
              chat.persistedStatus === 'FAILED' ||
              (chat.persistedStatus === 'INTERRUPTED' && !chat.stoppedByUser);

            return (
              <div key={chat.requestId}>
                <Dialogue
                  chat={chat}
                  deepThink={chat.deepThink}
                  changeTask={changeTask}
                  changeFile={changeFile}
                  changePlan={changePlan}
                  beforeResponse={
                    chat.orchestration &&
                    (chat.orchestration.route === 'ORCHESTRATED' ||
                      chat.orchestration.route === null) ? (
                      <div>
                        <OrchestrationTimeline
                          state={chat.orchestration}
                          onToggleMaster={() =>
                            patchOrchestration(chat.requestId, toggleMasterOpen)
                          }
                          onToggleMain={() =>
                            patchOrchestration(chat.requestId, toggleMainOpen)
                          }
                          onToggleStep={(attemptNo, stepId) =>
                            patchOrchestration(chat.requestId, (state) =>
                              toggleStepOpen(state, attemptNo, stepId),
                            )
                          }
                          onToggleSubTask={(attemptNo, stepId, subTaskId) =>
                            patchOrchestration(chat.requestId, (state) =>
                              toggleSubTaskOpen(
                                state,
                                attemptNo,
                                stepId,
                                subTaskId,
                              ),
                            )
                          }
                        />
                        {chat.orchestrationRecoveryWarning ? (
                          <div className="mt-4 text-[12px] text-text-tertiary">
                            编排时间线存在恢复告警，部分事件可能已跳过
                          </div>
                        ) : null}
                      </div>
                    ) : null
                  }
                />
                {chat.snapshotTruncated ? (
                  <div className="mt-8 text-[12px] text-text-tertiary">
                    部分工具明细已精简
                  </div>
                ) : null}
                {showErrorCard ? (
                  <div className="mt-8 mb-16 px-12 py-10 rounded-md bg-danger-soft text-danger text-[13px] border border-[rgba(217,45,32,0.12)]">
                    <div className="font-medium">
                      {chat.persistedStatus === 'INTERRUPTED'
                        ? '本次执行已中断，可重新发送'
                        : '执行失败'}
                    </div>
                    {chat.errorMessage ? (
                      <div className="mt-4">{chat.errorMessage}</div>
                    ) : null}
                    {chat.errorCode ? (
                      <div className="mt-4 text-[12px] opacity-80">
                        {chat.errorCode}
                      </div>
                    ) : null}
                  </div>
                ) : null}
              </div>
            );
          })}
          </div>
        </div>

        <div className="shrink-0 bg-surface">
          <div className="mx-auto w-full max-w-[960px] px-24 pb-20 pt-8">
        <GeneralInput
          placeholder={
            taskRunning
              ? '正在生成…'
              : inputDisabled
                ? '任务进行中'
                : '希望 Genie 为你做哪些任务呢？'
          }
          showBtn={false}
          size="medium"
          disabled={inputDisabled || (detachedRunning && !taskRunning)}
          running={taskRunning}
          onStop={stopGeneration}
          product={product}
          leftExtra={
            phase2 ? (
              <ExecutionModeSelector
                value={executionMode}
                disabled={inputDisabled || taskRunning || detachedRunning}
                allowedAgentIds={allowedAgentIds}
                onChange={(next) => {
                  onExecutionModeChange?.(next);
                }}
                onAllowedAgentIdsChange={(ids) =>
                  onAllowedAgentIdsChange?.(ids)
                }
              />
            ) : null
          }
          send={(info) =>
            void sendMessage({
              ...info,
              deepThink: sendMode.deepThink,
              outputStyle: sendMode.outputStyle,
            })
          }
        />
          </div>
        </div>
      </div>
      <div
        className={classNames(
          'h-full shrink-0 border-l border-border bg-surface-subtle transition-[width] duration-200',
          showAction
            ? 'w-[560px] max-w-[48vw]'
            : 'pointer-events-none w-0 overflow-hidden border-l-0',
        )}
      >
        <ActionView
          activeTask={activeTask}
          taskList={taskList}
          plan={plan}
          ref={actionViewRef}
          onClose={() => changeActionStatus(false)}
        />
      </div>
    </div>
  );
};

export default ChatView;
