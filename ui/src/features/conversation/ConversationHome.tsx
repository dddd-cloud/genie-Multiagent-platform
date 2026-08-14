import { useState, useCallback, memo, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { message, Image } from 'antd';
import classNames from 'classnames';
import GeneralInput from '@/components/GeneralInput';
import Slogn from '@/components/Slogn';
import DataAgentChatView from '@/components/DataAgentChatView';
import DataListDrawer from '@/components/DataListDrawer';
import ColsAndDataDrawer from '@/components/DataListDrawer/ColsAndDataDrawer';
import {
  productList,
  defaultProduct,
  chatQustions,
  demoList,
} from '@/utils/constants';
import { isPhase2Enabled } from '@/features/phase2/executionMode/featureFlag';
import ExecutionModeSelector from '@/features/phase2/executionMode/ExecutionModeSelector';
import type { ExecutionMode } from '@/contracts';
import { MvpApiError } from '@/services/apiError';
import { createConversation } from './api';
import { createRequestId } from './requestId';
import { useConversationLayout } from './ConversationLayout';
import type { ConversationDraft } from './types';

type HomeProps = Record<string, never>;

const ConversationHome: GenieType.FC<HomeProps> = memo(() => {
  const navigate = useNavigate();
  const layout = useConversationLayout();
  const [inputInfo, setInputInfo] = useState<CHAT.TInputInfo>({
    message: '',
    deepThink: false,
  });
  const [product, setProduct] = useState(defaultProduct);
  const [videoModalOpen, setVideoModalOpen] = useState<string | undefined>();
  const [dbsShow, setDbsShow] = useState(false);
  const [dataShow, setDataShow] = useState(false);
  const [curModel, setCurModel] = useState<CHAT.ModelInfo>({
    modelName: '',
    modelCode: '',
    schemaList: [],
  });
  const [sending, setSending] = useState(false);
  const sendingRef = useRef(false);
  const [executionMode, setExecutionMode] = useState<ExecutionMode>('AUTO');
  const [allowedAgentIds, setAllowedAgentIds] = useState<string[]>([]);

  const showDetail = useCallback((modelInfo: CHAT.ModelInfo) => {
    setCurModel(modelInfo);
    setDataShow(true);
  }, []);

  const handleSend = useCallback(
    async (info: CHAT.TInputInfo) => {
      const isDataAgentLite =
        product.type === 'dataAgent' && !info.deepThink;

      if (isDataAgentLite) {
        setInputInfo(info);
        return;
      }

      if (sendingRef.current) {
        return;
      }
      sendingRef.current = true;
      setSending(true);

      try {
        const requestId = createRequestId();
        await layout?.discardUnusedDrafts?.();
        const created = await createConversation(null);
        if (!created) {
          message.error('创建会话失败');
          return;
        }
        layout?.upsert({
          ...created,
          lastMessageAt: new Date().toISOString(),
          lastMessagePreview: null,
        });
        const draft: ConversationDraft = {
          requestId,
          inputInfo: info,
          productType: product.type,
          ...(isPhase2Enabled()
            ? {
              executionMode,
              allowedAgentIds,
            }
            : {}),
        };
        navigate(`/app/chat/${created.id}`, { state: draft });
      } catch (err: unknown) {
        if (err instanceof MvpApiError && err.code === 'AUTH_REQUIRED') {
          throw err;
        }
        if (err instanceof MvpApiError && err.code === 'ACCESS_DENIED') {
          message.error('无权限创建会话');
          return;
        }
        message.error(
          err instanceof MvpApiError ? err.message : '创建会话失败',
        );
      } finally {
        sendingRef.current = false;
        setSending(false);
      }
    },
    [layout, navigate, product.type, executionMode, allowedAgentIds],
  );

  const onDemoChip = useCallback(
    (query: { label: string; type: number }) => {
      void handleSend({
        message: query.label,
        outputStyle: 'dataAgent',
        deepThink: query.type === 2,
      });
    },
    [handleSend],
  );

  const CaseCard = ({
    title,
    description,
    tag,
    image,
    url,
    videoUrl,
  }: {
    title: string;
    description: string;
    tag: string;
    image: string;
    url: string;
    videoUrl: string;
  }) => {
    return (
      <div className="group flex flex-col rounded-lg bg-surface pt-16 px-16 shadow-xs hover:shadow-sm hover:border-border-strong transition-[border-color,box-shadow] duration-200 ease-in-out cursor-pointer w-full border border-border">
        <div className="mb-4 flex items-center justify-between gap-8">
          <div className="text-[14px] font-semibold text-text-primary truncate">
            {title}
          </div>
          <div className="shrink-0 inline-block bg-surface-subtle text-text-secondary px-[6px] leading-[20px] text-[12px] rounded-sm border border-border">
            {tag}
          </div>
        </div>
        <div className="text-[12px] text-text-secondary h-40 line-clamp-2 leading-[20px]">
          {description}
        </div>
        <div
          className="text-brand hover:text-brand-hover text-[12px] flex items-center mb-6 cursor-pointer transition-colors duration-150"
          onClick={() => window.open(url)}
        >
          <span className="mr-1">查看报告</span>
          <i className="font_family icon-xinjianjiantou"></i>
        </div>
        <div className="relative rounded-t-[10px] overflow-hidden h-100">
          <Image
            style={{ display: 'none' }}
            preview={{
              visible: videoModalOpen === videoUrl,
              destroyOnHidden: true,
              imageRender: () => (
                <video muted width="80%" controls autoPlay src={videoUrl} />
              ),
              toolbarRender: () => null,
              onVisibleChange: () => {
                setVideoModalOpen(undefined);
              },
            }}
            src={image}
          />
          <img
            src={image}
            className="w-full h-full rounded-t-[10px] mt-[-20px]"
            alt=""
          />
          <div
            className="absolute inset-0 flex items-center justify-center cursor-pointer rounded-t-[10px] hover:bg-[rgba(0,0,0,0.45)] border border-border transition-colors duration-150"
            onClick={() => setVideoModalOpen(videoUrl)}
          >
            <i className="font_family icon-bofang hidden group-hover:block text-white text-[24px]"></i>
          </div>
        </div>
      </div>
    );
  };

  const renderContent = () => {
    if (inputInfo.message.length > 0) {
      return <DataAgentChatView inputInfo={inputInfo} product={product} />;
    }

    return (
      <div className="w-full px-24 pt-[min(120px,12vh)] pb-48 flex flex-col items-center">
        <Slogn />
        <div className="w-full max-w-[720px]">
          <GeneralInput
            placeholder={product.placeholder}
            showBtn={true}
            size="big"
            disabled={sending}
            product={product}
            send={(info) => {
              void handleSend(info);
            }}
            dbsShow={setDbsShow}
            leftExtra={
              isPhase2Enabled() ? (
                <ExecutionModeSelector
                  value={executionMode}
                  disabled={sending}
                  allowedAgentIds={allowedAgentIds}
                  onChange={setExecutionMode}
                  onAllowedAgentIdsChange={setAllowedAgentIds}
                />
              ) : null
            }
          />
        </div>
        <div className="w-full max-w-[720px] grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-10 mt-[16px]">
          {productList.map((item, i) => (
            <div
              key={i}
              className={classNames(
                'h-[36px] cursor-pointer flex items-center justify-center border rounded-md px-8 transition-colors duration-150',
                item.type === product.type
                  ? 'border-brand bg-brand-soft text-brand'
                  : 'border-border bg-surface text-text-secondary hover:bg-surface-subtle',
              )}
              onClick={() => setProduct(item)}
            >
              <i className={`font_family ${item.img} ${item.color}`}></i>
              <div className="ml-[6px] text-[13px] truncate">{item.name}</div>
            </div>
          ))}
        </div>
        <div className="w-full max-w-[1100px] mt-80 mb-80 relative">
          <div
            className={classNames(
              'absolute top-[-45px] p-0 w-full overflow-hidden transition-all duration-400 opacity-0',
              { 'opacity-100 top-[-65px]': product.type === 'dataAgent' },
            )}
          >
            <div className="flex flex-wrap gap-x-[12px] gap-y-8 justify-center">
              {chatQustions.map((item, i) => (
                <div
                  key={i}
                  className="text-text-secondary cursor-pointer border border-border bg-surface rounded-md px-[16px] py-[4px] text-[14px] whitespace-nowrap flex items-center gap-[3px] hover:bg-surface-subtle transition-colors duration-150"
                  onClick={() => onDemoChip(item)}
                >
                  {item.type === 2 && (
                    <i className="font_family icon-shendusikao"></i>
                  )}
                  {item.label}
                </div>
              ))}
            </div>
          </div>
          <div className="text-center">
            <h2 className="text-[22px] font-semibold text-text-primary mb-8">
              优秀案例
            </h2>
            <p className="text-text-secondary">和 Genie 一起提升工作效率</p>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-16 mt-24">
            {demoList.map((demo, i) => (
              <CaseCard key={i} {...demo} />
            ))}
          </div>
        </div>
        <DataListDrawer
          show={dbsShow}
          dbsShow={setDbsShow}
          showDetail={showDetail}
        />
        {dataShow && (
          <ColsAndDataDrawer
            show={dataShow}
            dataShow={setDataShow}
            modelInfo={curModel}
          />
        )}
      </div>
    );
  };

  return (
    <div className="h-full overflow-auto bg-surface flex flex-col items-center">
      {renderContent()}
    </div>
  );
});

ConversationHome.displayName = 'ConversationHome';

export default ConversationHome;
