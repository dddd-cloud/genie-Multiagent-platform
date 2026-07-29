import { useEffect, useState, useRef } from 'react';
import { scrollToTop } from '@/utils';
import dataAgentSSE, { type DataAgentEvent } from '@/utils/dataAgentSSE';
import type { SseHandle } from '@/utils/querySSE';
import { notifyMvpError } from '@/features/auth/mvpErrorBus';
import { MvpApiError } from '@/services/apiError';
import DataDialogue from '@/components/Dialogue/DataDialogue';
import GeneralInput from '@/components/GeneralInput';
import Logo from '@/components/Logo';
import classNames from 'classnames';
import { message } from 'antd';

type Props = {
  inputInfo: CHAT.TInputInfo;
  product?: CHAT.Product;
};

/**
 * Survives React Strict Mode remount so the home→DataAgent first query
 * is POSTed once (plan §9.2: abort on unmount, no automatic POST replay).
 */
const consumedInitialDataAgentKeys = new Set<string>();

/**
 * Direct DataAgent path (no conversation persistence).
 * Cookie + CSRF + abort; AUTH_REQUIRED / CSRF_INVALID / ACCESS_DENIED match plan §7.5–7.6.
 */
const DataAgentChatView: GenieType.FC<Props> = (props) => {
  const { inputInfo: inputInfoProp, product } = props;

  const [chatTitle, setChatTitle] = useState('');
  const [dataChatList, setDataChatList] = useState<Record<string, any>[]>([]);
  const [loading, setLoading] = useState(false);
  const chatRef = useRef<HTMLDivElement>(null);
  const dataChatListRef = useRef<Record<string, any>[]>([]);
  const sseHandleRef = useRef<SseHandle | null>(null);
  const sendGenRef = useRef(0);
  const mountedRef = useRef(true);
  const initialKeyRef = useRef(
    `${product?.type ?? 'dataAgent'}::${inputInfoProp.message ?? ''}`,
  );

  useEffect(() => {
    dataChatListRef.current = dataChatList;
  }, [dataChatList]);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      sseHandleRef.current?.abort();
      sseHandleRef.current = null;
    };
  }, []);

  const handleHttpError = (httpStatus: number, code?: string, msg?: string) => {
    // Plan §7.5: only HTTP 401 + AUTH_REQUIRED means session expired.
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
      return;
    }
    if (code === 'ACCESS_DENIED') {
      message.error(msg || '无权限执行该操作');
      return;
    }
    message.error(msg || `请求失败 (${httpStatus})`);
  };

  const sendDataMessage = (inputInfo: CHAT.TInputInfo) => {
    if (sseHandleRef.current) {
      sseHandleRef.current.abort();
      sseHandleRef.current = null;
    }

    const sendGen = ++sendGenRef.current;
    const params = {
      content: inputInfo.message,
    };
    const currentChat: Record<string, any> = {
      query: inputInfo.message,
      loading: true,
      think: '',
      chartData: undefined,
      error: '',
    };
    const nextList = [...dataChatListRef.current, currentChat];
    dataChatListRef.current = nextList;
    setDataChatList(nextList);
    if (chatRef.current) {
      scrollToTop(chatRef.current);
    }

    setChatTitle(inputInfo.message ?? '');
    setLoading(true);

    const handleMessage = (data: DataAgentEvent) => {
      if (sendGen !== sendGenRef.current) {
        return;
      }
      switch (data.eventType) {
        case 'THINK':
          currentChat.think = data.data;
          break;
        case 'CHART_DATA':
          currentChat.chartData = data.data;
          break;
        case 'ERROR':
          currentChat.error = data.data;
          currentChat.loading = false;
          setLoading(false);
          break;
        case 'READY':
          currentChat.loading = false;
          setLoading(false);
          break;
        default:
          break;
      }
      const refreshed = [...dataChatListRef.current];
      refreshed[refreshed.length - 1] = { ...currentChat };
      dataChatListRef.current = refreshed;
      setDataChatList(refreshed);
      if (chatRef.current) {
        scrollToTop(chatRef.current);
      }
    };

    const handle = dataAgentSSE({
      body: params,
      handleMessage,
    });
    sseHandleRef.current = handle;

    void handle.done.then((result) => {
      // Superseded by a newer send, or unmounted — ignore stale terminal.
      if (sendGen !== sendGenRef.current || !mountedRef.current) {
        return;
      }
      if (sseHandleRef.current === handle) {
        sseHandleRef.current = null;
      }
      if (result.kind === 'HTTP_ERROR') {
        currentChat.loading = false;
        if (!currentChat.error) {
          currentChat.error = result.message ?? `HTTP ${result.httpStatus}`;
        }
        const refreshed = [...dataChatListRef.current];
        refreshed[refreshed.length - 1] = { ...currentChat };
        dataChatListRef.current = refreshed;
        setDataChatList(refreshed);
        setLoading(false);
        handleHttpError(result.httpStatus, result.code, result.message);
        return;
      }
      if (result.kind === 'FAILED' || result.kind === 'INTERRUPTED') {
        currentChat.loading = false;
        if (!currentChat.error) {
          currentChat.error =
            result.kind === 'FAILED'
              ? result.errorMsg ?? '执行失败'
              : result.message ?? '连接已断开';
        }
        const refreshed = [...dataChatListRef.current];
        refreshed[refreshed.length - 1] = { ...currentChat };
        dataChatListRef.current = refreshed;
        setDataChatList(refreshed);
        setLoading(false);
        return;
      }
      if (result.kind === 'COMPLETED') {
        currentChat.loading = false;
        setLoading(false);
      }
    });
  };

  useEffect(() => {
    const key = initialKeyRef.current;
    if (!inputInfoProp.message?.length) {
      return;
    }
    if (consumedInitialDataAgentKeys.has(key)) {
      return;
    }
    consumedInitialDataAgentKeys.add(key);
    sendDataMessage(inputInfoProp);
    // First message from home only.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="h-full w-full flex justify-center">
      <div
        className={classNames('p-24 flex flex-col flex-1 w-0 max-w-[1200px]')}
      >
        <div className="w-full flex justify-between">
          <div className="w-full flex items-center pb-8">
            <Logo />
            <div className="overflow-hidden whitespace-nowrap text-ellipsis text-[16px] font-[500] text-[#27272A] mr-8">
              {chatTitle}
            </div>
          </div>
        </div>
        <div
          className="w-full flex-1 overflow-auto no-scrollbar mb-[36px]"
          ref={chatRef}
        >
          {dataChatList.map((chat, index) => (
            <div key={index}>
              <DataDialogue chat={chat} />
            </div>
          ))}
        </div>
        <GeneralInput
          placeholder={loading ? '任务进行中' : '希望 Genie 为你做哪些任务呢？'}
          showBtn={false}
          size="medium"
          disabled={loading}
          product={product}
          send={(info) => sendDataMessage(info)}
        />
      </div>
    </div>
  );
};

export default DataAgentChatView;
