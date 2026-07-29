import { useEffect, useMemo, useRef, useState } from 'react';
import { message } from 'antd';
import classNames from 'classnames';
import { useMemoizedFn } from 'ahooks';
import type { OutputStyle } from '@/contracts';
import { OUTPUT_STYLES } from '@/contracts';
import { notifyMvpError } from '@/features/auth/mvpErrorBus';
import type { PersistedChatItem } from '@/features/conversation/types';
import { isUuid } from '@/features/conversation/requestId';
import { MvpApiError } from '@/services/apiError';
import {
  ActionViewItemEnum,
  scrollToTop,
} from '@/utils';
import { RESULT_TYPES, productList } from '@/utils/constants';
import { combineData, handleTaskData } from '@/utils/chat';
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
  } = props;

  const sessionId = conversationId;
  const [chatList, setChatList] = useState<PersistedChatItem[]>(initialChats);
  const [taskList, setTaskList] = useState<MESSAGE.Task[]>([]);
  const [activeTask, setActiveTask] = useState<CHAT.Task>();
  const [plan, setPlan] = useState<CHAT.Plan>();
  const [showAction, setShowAction] = useState(false);
  const [sendInFlight, setSendInFlight] = useState(false);
  const [reconcileHint, setReconcileHint] = useState<string | null>(null);
  const [needManualRefresh, setNeedManualRefresh] = useState(false);
  const [reconciling, setReconciling] = useState(false);

  const chatRef = useRef<HTMLDivElement>(null);
  const actionViewRef = ActionView.useActionView();

  const sseHandleRef = useRef<SseHandle | null>(null);
  const sendInFlightRef = useRef(false);
  const openedOnceRef = useRef(false);
  const reconcileStartedRef = useRef(false);
  const mountedRef = useRef(true);
  const initialChatsRef = useRef(initialChats);
  initialChatsRef.current = initialChats;

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

  const inputDisabled =
    sendInFlight || detachedRunning || reconciling;

  useEffect(() => {
    if (!sendInFlightRef.current) {
      setChatList(initialChats);
    }
  }, [initialChats]);

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

  const stopLoadingForRequest = useMemoizedFn((requestId: string, patch?: Partial<PersistedChatItem>) => {
    setChatList((prev) =>
      prev.map((item) =>
        item.requestId === requestId
          ? {
            ...item,
            loading: false,
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
      setChatList((prev) => [...prev, loadingChat]);
      sendInFlightRef.current = true;
      setSendInFlight(true);
      openedOnceRef.current = false;

      const body: Record<string, unknown> = {
        sessionId,
        requestId,
        query,
        deepThink: deepThink ? 1 : 0,
        outputStyle,
      };

      const handleMessage = (data: MESSAGE.Answer) => {
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
          working.loading = false;
        }

        const taskData = handleTaskData(working, deepThink, working.multiAgent);
        setTaskList(taskData.taskList);
        temporaryChangeTask(taskData.taskList);
        if (taskData.plan) {
          updatePlan(taskData.plan);
        }
        openAction(taskData.taskList);

        const nextItem: PersistedChatItem = { ...working };
        currentChat = nextItem;
        setChatList((prev) => {
          const next = [...prev];
          const idx = next.findIndex((c) => c.requestId === requestId);
          if (idx >= 0) {
            next[idx] = nextItem;
          }
          return next;
        });

        if (chatRef.current) {
          scrollToTop(chatRef.current);
        }
      };

      const handle = querySSE({
        body,
        handleMessage,
        onOpen: () => {
          if (!openedOnceRef.current) {
            openedOnceRef.current = true;
            void onConversationChanged();
          }
        },
      });
      sseHandleRef.current = handle;

      const result = await handle.done;
      sseHandleRef.current = null;

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
        await onReloadMessages();
        await onConversationChanged();
        return;
      }

      if (result.kind === 'HTTP_ERROR') {
        // Stop local loading only — do not invent FAILED; reload (except AUTH)
        // clears optimistic turns the backend never accepted.
        stopLoadingForRequest(requestId, { tip: '' });
        sendInFlightRef.current = false;
        setSendInFlight(false);
        await handleHttpError(result);
        return;
      }

      // INTERRUPTED — plan §11.9/§11.10: never invent DB INTERRUPTED locally;
      // show disconnect hint and reconcile from backend.
      const malformed = isMalformedStreamMessage(result.message);
      stopLoadingForRequest(requestId, { tip: '' });
      sendInFlightRef.current = false;
      setSendInFlight(false);

      if (result.reason === 'ABORT') {
        // Unmount abort: dying instance must not reconcile (new route loads history).
        // Still-mounted abort (e.g. AUTH/Strict Mode race): plan §11.10 有限对账.
        // Keep requestId in consumedDraftRequestIds — never re-POST same draft UUID.
        if (mountedRef.current) {
          await runLimitedReconcile(true);
        }
        return;
      }

      if (malformed) {
        message.error('流式响应格式错误');
        await onReloadMessages();
        return;
      }

      await runLimitedReconcile(true);
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
    if (!detachedRunning) {
      return;
    }
    reconcileStartedRef.current = true;
    void runLimitedReconcile(false);
  }, [detachedRunning, initialDraft, runLimitedReconcile]);

  // Abort SSE on unmount
  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      sseHandleRef.current?.abort();
      sseHandleRef.current = null;
    };
  }, []);

  return (
    <div className="h-full w-full flex justify-center bg-surface">
      <div
        className={classNames('px-24 py-20 flex flex-col flex-1 w-0', {'max-w-[960px]': !showAction,})}
        id="chat-view"
      >
        <div className="w-full flex justify-between border-b border-border pb-12 mb-4">
          <div className="w-full flex items-center min-w-0">
            <Logo />
            <div className="overflow-hidden whitespace-nowrap text-ellipsis text-[16px] font-medium text-text-primary mr-8">
              {conversationTitle}
            </div>
            {sendMode.deepThink ? (
              <div className="rounded-sm px-8 py-2 border border-border bg-surface-subtle text-text-secondary flex items-center shrink-0 text-[12px]">
                <i className="font_family icon-shendusikao mr-6 text-[12px]"></i>
                <span className="ml-[-4px]">深度研究</span>
              </div>
            ) : null}
          </div>
        </div>

        {reconcileHint ? (
          <div className="mb-12 px-12 py-8 rounded-md bg-surface-subtle border border-border text-[13px] text-text-secondary flex items-center gap-12">
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
        ) : null}

        <div
          className="w-full flex-1 overflow-auto no-scrollbar mb-[28px]"
          ref={chatRef}
        >
          {chatList.map((chat) => {
            const showErrorCard =
              chat.persistedStatus === 'FAILED' ||
              chat.persistedStatus === 'INTERRUPTED';

            return (
              <div key={chat.requestId}>
                <Dialogue
                  chat={chat}
                  deepThink={chat.deepThink}
                  changeTask={changeTask}
                  changeFile={changeFile}
                  changePlan={changePlan}
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

        <GeneralInput
          placeholder={
            inputDisabled ? '任务进行中' : '希望 Genie 为你做哪些任务呢？'
          }
          showBtn={false}
          size="medium"
          disabled={inputDisabled}
          product={product}
          send={(info) =>
            void sendMessage({
              ...info,
              deepThink: sendMode.deepThink,
              outputStyle: sendMode.outputStyle,
            })
          }
        />
      </div>
      <div
        className={classNames('transition-all w-0 border-l border-border bg-surface-subtle', {
          'opacity-0 overflow-hidden border-l-0': !showAction,
          'flex-1': showAction,
        })}
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
