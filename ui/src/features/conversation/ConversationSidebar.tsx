import { memo, useCallback, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Button, Input, Modal, Spin } from 'antd';
import { DeleteOutlined, EditOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import classNames from 'classnames';
import type { ConversationListState } from './conversationReducer';

const TITLE_MAX_CODE_POINTS = 200;

export function unicodeLength(str: string): number {
  return [...str].length;
}

/** Truncate by Unicode code points (not UTF-16 code units). */
export function truncateToCodePoints(str: string, max: number): string {
  const chars = [...str];
  if (chars.length <= max) {
    return str;
  }
  return chars.slice(0, max).join('');
}

export function isValidConversationTitle(title: string): boolean {
  const trimmed = title.trim();
  if (!trimmed) {
    return false;
  }
  return unicodeLength(trimmed) <= TITLE_MAX_CODE_POINTS;
}

type Props = {
  state: ConversationListState;
  onRename: (id: string, title: string) => void | Promise<void>;
  onDelete: (id: string) => void | Promise<void>;
  onLoadMore: () => void;
  onRetry: () => void;
  onSelect: (id: string) => void;
};

const ConversationSidebar: GenieType.FC<Props> = memo((props) => {
  const {
    state,
    onRename,
    onDelete,
    onLoadMore,
    onRetry,
    onSelect,
  } = props;
  const { conversationId } = useParams<{ conversationId?: string }>();
  const [modal, modalContextHolder] = Modal.useModal();
  const [renameId, setRenameId] = useState<string | null>(null);
  const [renameValue, setRenameValue] = useState('');
  const [renameSubmitting, setRenameSubmitting] = useState(false);

  const renameTarget = useMemo(
    () => state.items.find((item) => item.id === renameId) ?? null,
    [renameId, state.items],
  );

  const openRename = useCallback((id: string, title: string) => {
    setRenameId(id);
    setRenameValue(title);
  }, []);

  const closeRename = useCallback(() => {
    setRenameId(null);
    setRenameValue('');
    setRenameSubmitting(false);
  }, []);

  const submitRename = useCallback(async () => {
    if (!renameId) {
      return;
    }
    const next = renameValue.trim();
    if (!isValidConversationTitle(next)) {
      return;
    }
    setRenameSubmitting(true);
    try {
      await onRename(renameId, next);
      closeRename();
    } catch {
      setRenameSubmitting(false);
    }
  }, [closeRename, onRename, renameId, renameValue]);

  const confirmDelete = useCallback(
    (id: string, title: string) => {
      // Use hook modal (not Modal.confirm static) so the dialog mounts in React tree.
      modal.confirm({
        title: '删除会话',
        content: `确定删除「${title}」？此操作不可恢复。`,
        okText: '删除',
        okType: 'danger',
        cancelText: '取消',
        centered: true,
        onOk: () => Promise.resolve(onDelete(id)),
      });
    },
    [modal, onDelete],
  );

  const formatTime = (value: string | null) => {
    if (!value) {
      return '';
    }
    return dayjs(value).format('MM-DD HH:mm');
  };

  return (
    <aside className="h-full w-full flex flex-col bg-sidebar">
      {modalContextHolder}
      <div className="flex-1 overflow-auto px-10 pb-8 pt-4">
        <div className="px-10 pt-4 pb-8 text-[14px] font-semibold text-text-primary leading-[22px]">
          最近
        </div>

        {state.loading && state.items.length === 0 ? (
          <div className="flex justify-center py-40">
            <Spin />
          </div>
        ) : null}

        {!state.loading && state.error && state.items.length === 0 ? (
          <div className="text-center py-40 px-12">
            <div className="text-text-secondary text-[13px] mb-12">
              {state.error}
            </div>
            <Button onClick={onRetry}>重试</Button>
          </div>
        ) : null}

        {!state.loading &&
        !state.error &&
        state.items.length === 0 ? (
            <div className="text-center py-40 text-text-tertiary text-[13px]">
            暂无会话，点击上方新建
            </div>
          ) : null}

        {state.items.map((item) => {
          const active = item.id === conversationId;
          return (
            <div
              key={item.id}
              className={classNames(
                'group rounded-[8px] px-10 py-8 mb-1 cursor-pointer transition-colors duration-150',
                {
                  'bg-[#F0F0F2]': active,
                  'hover:bg-[#F5F5F7]': !active,
                },
              )}
              onClick={() => onSelect(item.id)}
            >
              <div className="flex items-center justify-between gap-8">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-6 min-w-0">
                    <div className="text-[14px] text-text-primary truncate leading-[22px]">
                      {item.title}
                    </div>
                    {item.privacyMode ? (
                      <span className="shrink-0 rounded-full bg-[#F0F0F2] px-6 py-1 text-[10px] text-text-tertiary">
                        隐私
                      </span>
                    ) : null}
                  </div>
                  {item.lastMessageAt ? (
                    <div className="text-[11px] text-text-tertiary mt-2 leading-[16px]">
                      {formatTime(item.lastMessageAt)}
                    </div>
                  ) : null}
                </div>
                <div
                  className="shrink-0 flex items-center gap-2"
                  onClick={(e) => e.stopPropagation()}
                >
                  <button
                    type="button"
                    className="inline-flex items-center justify-center size-24 rounded-sm text-text-tertiary opacity-0 group-hover:opacity-70 hover:!opacity-100 hover:bg-surface focus-visible:opacity-100 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand transition-opacity duration-150"
                    aria-label={`重命名 ${item.title}`}
                    onClick={() => openRename(item.id, item.title)}
                  >
                    <EditOutlined className="text-[13px]" />
                  </button>
                  <button
                    type="button"
                    className="inline-flex items-center justify-center size-24 rounded-sm text-danger opacity-0 group-hover:opacity-70 hover:!opacity-100 hover:bg-danger-soft focus-visible:opacity-100 focus-visible:outline focus-visible:outline-2 focus-visible:outline-danger transition-opacity duration-150"
                    aria-label={`删除 ${item.title}`}
                    onClick={() => confirmDelete(item.id, item.title)}
                  >
                    <DeleteOutlined className="text-[13px]" />
                  </button>
                </div>
              </div>
            </div>
          );
        })}

        {state.hasMore && state.items.length > 0 ? (
          <div className="py-8">
            <Button
              block
              loading={state.loadingMore}
              onClick={onLoadMore}
              disabled={state.loadingMore}
            >
              加载更多
            </Button>
          </div>
        ) : null}

        {state.error && state.items.length > 0 ? (
          <div className="text-center py-8">
            <div className="text-[12px] text-danger mb-8">{state.error}</div>
            <Button size="small" onClick={onRetry}>
              重试
            </Button>
          </div>
        ) : null}
      </div>

      <Modal
        title="重命名会话"
        open={!!renameTarget}
        onCancel={closeRename}
        onOk={submitRename}
        okButtonProps={{
          disabled: !isValidConversationTitle(renameValue),
          loading: renameSubmitting,
        }}
        destroyOnClose
      >
        <Input
          value={renameValue}
          onChange={(e) =>
            setRenameValue(
              truncateToCodePoints(e.target.value, TITLE_MAX_CODE_POINTS),
            )
          }
          onPressEnter={submitRename}
          placeholder="请输入会话标题"
        />
        <div className="text-[12px] text-text-tertiary mt-8">
          {unicodeLength(renameValue.trim())}/{TITLE_MAX_CODE_POINTS}
        </div>
      </Modal>
    </aside>
  );
});

ConversationSidebar.displayName = 'ConversationSidebar';

export default ConversationSidebar;
