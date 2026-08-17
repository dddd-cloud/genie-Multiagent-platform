import { memo, useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Drawer,
  Input,
  Modal,
  Spin,
  message,
} from 'antd';
import {
  DeleteOutlined,
  DownOutlined,
  PlusOutlined,
  RightOutlined,
} from '@ant-design/icons';
import { listConversations } from '@/features/conversation/api';
import {
  emptyLongTermMemoryDoc,
  serializeLongTermMemory,
} from './markdownSerializer';
import {
  conversationDisplayTitle,
  formatHostMemoryPath,
  formatRelativeUpdate,
  LONG_TERM_SECTION_UI,
  summaryPreview,
} from './memorySettingsUi';
import { conversationIdFromSummaryPath } from './paths';
import { useLocalMemory } from './useLocalMemory';
import {
  LONG_TERM_SECTION_NAMES,
  SUMMARY_SECTION_NAMES,
  type ConversationSummaryDoc,
  type LongTermMemoryDoc,
  type LongTermMemoryEntry,
  type LongTermSectionName,
  type MemoryIndexRecord,
  type MemoryTaskRecord,
} from './types';

type ConversationNote = {
  conversationId: string;
  title: string;
  preview: string;
  updatedAt: string;
  doc: ConversationSummaryDoc | null;
  raw: string;
  corrupted: boolean;
};

type EntryEditorState = {
  section: LongTermSectionName;
  index: number | null;
  key: string;
  value: string;
};

type PendingDelete =
  | { kind: 'entry'; section: LongTermSectionName; index: number }
  | { kind: 'note'; conversationId: string }
  | { kind: 'all' };

const MemorySettingsPage: GenieType.FC = memo(() => {
  const memory = useLocalMemory();
  const [doc, setDoc] = useState<LongTermMemoryDoc>(() => emptyLongTermMemoryDoc());
  const [rawLongTerm, setRawLongTerm] = useState('');
  const [notes, setNotes] = useState<ConversationNote[]>([]);
  const [tasks, setTasks] = useState<MemoryTaskRecord[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [corrupted, setCorrupted] = useState(false);
  const [busy, setBusy] = useState(false);
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [editor, setEditor] = useState<EntryEditorState | null>(null);
  const [savingEntry, setSavingEntry] = useState(false);
  const [openNote, setOpenNote] = useState<ConversationNote | null>(null);
  const [pendingDelete, setPendingDelete] = useState<PendingDelete | null>(null);
  const [deleting, setDeleting] = useState(false);

  const unavailable = memory.opfsStatus === 'UNAVAILABLE';
  const failedCount = useMemo(
    () => tasks.filter((task) => task.status === 'FAILED').length,
    [tasks],
  );
  const entryCount = useMemo(
    () =>
      LONG_TERM_SECTION_NAMES.reduce(
        (sum, section) => sum + doc.sections[section].length,
        0,
      ),
    [doc],
  );
  const showEmptyHint = !corrupted && !unavailable && entryCount === 0 && notes.length === 0;

  const reload = useCallback(async () => {
    setBusy(true);
    setLoadError(null);
    setCorrupted(false);
    try {
      await memory.refreshStatus();
      const repo = memory.repository;
      if (!repo) {
        setDoc(emptyLongTermMemoryDoc());
        setRawLongTerm('');
        setNotes([]);
        setTasks([]);
        return;
      }

      const ltm = await repo.readLongTermMemory();
      if (ltm.status === 'READY') {
        setDoc(ltm.doc);
        setRawLongTerm(ltm.raw);
      } else if (ltm.status === 'EMPTY') {
        setDoc(emptyLongTermMemoryDoc());
        setRawLongTerm('');
      } else if (ltm.status === 'CORRUPTED') {
        setDoc(emptyLongTermMemoryDoc());
        setRawLongTerm(ltm.raw);
        setCorrupted(true);
        setLoadError('记忆文件损坏，需要修复后才能继续使用。');
      } else if (ltm.status === 'UNAVAILABLE') {
        setDoc(emptyLongTermMemoryDoc());
        setRawLongTerm('');
        setLoadError('暂时无法保存记忆，聊天不受影响。');
      } else {
        setDoc(emptyLongTermMemoryDoc());
        setRawLongTerm('');
        setLoadError(ltm.message);
      }

      const [index, nextTasks, titles] = await Promise.all([
        memory.listSummaryIndex(),
        memory.listTasks(),
        loadConversationTitles(),
      ]);
      setTasks(nextTasks);
      setNotes(await loadConversationNotes(index, repo.readConversationSummary.bind(repo), titles));
    } finally {
      setBusy(false);
    }
  }, [memory]);

  useEffect(() => {
    void reload();
  }, [memory.userId, memory.repository]);

  const persistDoc = async (next: LongTermMemoryDoc) => {
    const repo = memory.repository;
    if (!repo) {
      throw new Error('暂时无法保存记忆');
    }
    const stamped = {
      ...next,
      updatedAt: new Date().toISOString(),
    };
    await repo.writeLongTermMemory(stamped);
    setDoc(stamped);
    setRawLongTerm(serializeLongTermMemory(stamped));
  };

  const saveEditor = async () => {
    if (!editor) {
      return;
    }
    const key = editor.key.trim();
    const value = editor.value.trim();
    if (!key || !value) {
      message.warning('请填写名称和内容');
      return;
    }
    const list = [...doc.sections[editor.section]];
    const duplicate = list.findIndex(
      (entry, idx) => entry.key === key && idx !== editor.index,
    );
    if (duplicate >= 0) {
      message.warning('这一组里已经有同名条目');
      return;
    }
    setSavingEntry(true);
    try {
      if (editor.index == null) {
        list.push({ key, value });
      } else {
        list[editor.index] = { key, value };
      }
      await persistDoc({
        ...doc,
        sections: {
          ...doc.sections,
          [editor.section]: list,
        },
      });
      setEditor(null);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSavingEntry(false);
    }
  };

  const confirmPendingDelete = async () => {
    if (!pendingDelete) {
      return;
    }
    setDeleting(true);
    try {
      if (pendingDelete.kind === 'entry') {
        const list = doc.sections[pendingDelete.section].filter(
          (_, idx) => idx !== pendingDelete.index,
        );
        await persistDoc({
          ...doc,
          sections: {
            ...doc.sections,
            [pendingDelete.section]: list,
          },
        });
        message.success('已删除');
      } else if (pendingDelete.kind === 'note') {
        await memory.clearConversationSummary(pendingDelete.conversationId);
        if (openNote?.conversationId === pendingDelete.conversationId) {
          setOpenNote(null);
        }
        message.success('已删除');
        await reload();
      } else {
        setBusy(true);
        await memory.clearLongTermMemory();
        await Promise.all(
          notes.map((note) => memory.clearConversationSummary(note.conversationId)),
        );
        message.success('已清空记忆');
        await reload();
      }
      setPendingDelete(null);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    } finally {
      setDeleting(false);
      setBusy(false);
    }
  };

  const repairLongTerm = async () => {
    await memory.rebuildLongTermMemory();
    message.success('已修复记忆文件');
    await reload();
  };

  const retryFailed = async () => {
    await memory.retryFailedTasks();
    await reload();
  };

  const exportLongTerm = () => {
    const content = rawLongTerm || serializeLongTermMemory(doc);
    if (!content.trim()) {
      message.warning('还没有可导出的长期记忆');
      return;
    }
    memory.exportTextFile('长期记忆.md', content);
  };

  const exportNote = (note: ConversationNote) => {
    if (!note.raw) {
      message.warning('这场笔记无法导出');
      return;
    }
    memory.exportTextFile(
      `${note.title.replace(/[\\/:*?"<>|]/g, '_')}-对话摘要.md`,
      note.raw,
    );
  };

  const editing = unavailable || corrupted || deleting;

  return (
    <div className="h-full overflow-auto bg-page">
      <div className="mx-auto max-w-[640px] px-24 py-36">
        <header className="mb-28">
          <h1 className="m-0 text-[28px] font-semibold tracking-[-0.02em] text-text-primary">
            记忆
          </h1>
          <p className="mt-8 mb-0 text-[15px] leading-[22px] text-text-secondary">
            我会记住你明确说过的偏好和长期信息，并只保存在这台电脑上。
          </p>
        </header>

        {unavailable ? (
          <Alert
            className="mb-20"
            type="warning"
            showIcon
            message="暂时无法保存记忆，聊天不受影响"
          />
        ) : null}

        {corrupted ? (
          <Alert
            className="mb-20"
            type="error"
            showIcon
            message="记忆文件损坏"
            description={loadError}
            action={
              <Button size="small" onClick={() => void repairLongTerm()}>
                修复
              </Button>
            }
          />
        ) : null}

        {!unavailable && !corrupted && loadError ? (
          <Alert className="mb-20" type="warning" showIcon message={loadError} />
        ) : null}

        {failedCount > 0 ? (
          <Alert
            className="mb-20"
            type="warning"
            showIcon
            message="有些记忆没更新成功"
            action={
              <Button size="small" onClick={() => void retryFailed()}>
                重试
              </Button>
            }
          />
        ) : null}

        {showEmptyHint ? (
          <div className="mb-28 rounded-xl bg-surface px-24 py-36 text-center shadow-xs">
            <p className="m-0 text-[16px] font-medium text-text-primary">还没有记忆</p>
            <p className="mt-8 mb-0 text-[14px] leading-[22px] text-text-secondary">
              聊得越具体，这里会出现你愿意被记住的内容。
            </p>
          </div>
        ) : null}

        <Spin spinning={busy && entryCount === 0 && notes.length === 0}>
          <section className="flex flex-col gap-28">
            {LONG_TERM_SECTION_NAMES.map((section) => (
              <MemoryGroup
                key={section}
                title={LONG_TERM_SECTION_UI[section].title}
                hint={LONG_TERM_SECTION_UI[section].hint}
                entries={doc.sections[section]}
                disabled={editing}
                onAdd={() =>
                  setEditor({
                    section,
                    index: null,
                    key: '',
                    value: '',
                  })
                }
                onEdit={(index) =>
                  setEditor({
                    section,
                    index,
                    key: doc.sections[section][index].key,
                    value: doc.sections[section][index].value,
                  })
                }
                onDelete={(index) =>
                  setPendingDelete({ kind: 'entry', section, index })
                }
              />
            ))}
          </section>
        </Spin>

        <section className="mt-36">
          <h2 className="m-0 mb-12 px-4 text-[13px] font-medium tracking-[0.02em] text-text-tertiary">
            对话笔记
          </h2>
          {notes.length === 0 ? (
            <div className="rounded-xl bg-surface px-16 py-20 text-[14px] text-text-secondary shadow-xs">
              还没有对话笔记。聊过几轮之后，这里会出现这场对话里记住的要点。
            </div>
          ) : (
            <div className="overflow-hidden rounded-xl bg-surface shadow-xs">
              {notes.map((note, index) => (
                <button
                  key={note.conversationId}
                  type="button"
                  className={[
                    'flex w-full cursor-pointer items-center gap-12 bg-transparent px-16 py-14 text-left',
                    index < notes.length - 1 ? 'border-0 border-b border-solid border-border' : '',
                  ].join(' ')}
                  onClick={() => setOpenNote(note)}
                >
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-[15px] font-medium text-text-primary">
                      {note.title}
                    </div>
                    <div className="mt-4 line-clamp-2 text-[13px] leading-[20px] text-text-secondary">
                      {note.preview}
                    </div>
                    <div className="mt-4 text-[12px] text-text-tertiary">
                      {formatRelativeUpdate(note.updatedAt)}
                    </div>
                  </div>
                  <RightOutlined className="text-[11px] text-text-tertiary" />
                </button>
              ))}
            </div>
          )}
        </section>

        <div className="mt-28 px-4">
          <button
            type="button"
            className="border-0 bg-transparent p-0 text-[13px] text-text-tertiary hover:text-danger"
            disabled={editing || (entryCount === 0 && notes.length === 0)}
            onClick={() => setPendingDelete({ kind: 'all' })}
          >
            清空全部记忆
          </button>
        </div>

        <div className="mt-36">
          <button
            type="button"
            data-testid="memory-advanced-toggle"
            className="flex items-center gap-6 border-0 bg-transparent p-0 text-[13px] text-text-tertiary"
            onClick={() => setAdvancedOpen((open) => !open)}
            aria-expanded={advancedOpen}
          >
            高级
            <DownOutlined
              className={['text-[10px] transition-transform', advancedOpen ? 'rotate-180' : ''].join(
                ' ',
              )}
            />
          </button>
          {advancedOpen ? (
            <div className="mt-12 overflow-hidden rounded-xl bg-surface px-16 py-16 text-[13px] text-text-secondary shadow-xs">
              <div className="flex flex-col gap-10">
                <div>
                  <div className="text-text-tertiary">存储位置</div>
                  <div className="mt-2 text-text-primary">
                    {formatHostMemoryPath(memory.diskRootPath)}
                  </div>
                </div>
                <div>
                  <div className="text-text-tertiary">账户</div>
                  <div className="mt-2 break-all text-text-primary" data-testid="memory-account-scope">
                    当前 userId 作用域：{memory.userId}
                  </div>
                </div>
                <div className="flex flex-wrap gap-8 pt-4">
                  <Button size="small" onClick={exportLongTerm}>
                    导出长期记忆
                  </Button>
                </div>
                <div className="pt-8">
                  <div className="mb-8 text-text-tertiary">原始文件</div>
                  <Input.TextArea
                    value={rawLongTerm}
                    readOnly
                    autoSize={{ minRows: 6, maxRows: 16 }}
                    className="font-mono text-[12px]"
                  />
                </div>
              </div>
            </div>
          ) : null}
        </div>
      </div>

      <Modal
        title={editor?.index == null ? '添加一条记忆' : '编辑记忆'}
        open={editor != null}
        onCancel={() => setEditor(null)}
        onOk={() => void saveEditor()}
        okText="保存"
        cancelText="取消"
        confirmLoading={savingEntry}
        destroyOnHidden
      >
        <div className="flex flex-col gap-12 pt-8">
          <Input
            placeholder={
              editor
                ? LONG_TERM_SECTION_UI[editor.section].keyPlaceholder
                : LONG_TERM_SECTION_UI.基本信息.keyPlaceholder
            }
            value={editor?.key ?? ''}
            maxLength={64}
            onChange={(event) =>
              setEditor((current) =>
                current ? { ...current, key: event.target.value } : current,
              )
            }
          />
          <Input.TextArea
            placeholder={
              editor
                ? LONG_TERM_SECTION_UI[editor.section].valuePlaceholder
                : LONG_TERM_SECTION_UI.基本信息.valuePlaceholder
            }
            value={editor?.value ?? ''}
            maxLength={500}
            autoSize={{ minRows: 3, maxRows: 6 }}
            onChange={(event) =>
              setEditor((current) =>
                current ? { ...current, value: event.target.value } : current,
              )
            }
          />
        </div>
      </Modal>

      <Drawer
        title={openNote?.title ?? '对话笔记'}
        open={openNote != null}
        onClose={() => setOpenNote(null)}
        width={420}
        extra={
          openNote ? (
            <div className="flex gap-8">
              <Button size="small" onClick={() => exportNote(openNote)}>
                导出
              </Button>
              <Button
                size="small"
                danger
                onClick={() =>
                  setPendingDelete({
                    kind: 'note',
                    conversationId: openNote.conversationId,
                  })
                }
              >
                删除
              </Button>
            </div>
          ) : null
        }
      >
        {openNote?.corrupted ? (
          <Alert type="error" showIcon message="这场笔记文件已损坏，可删除后重新生成。" />
        ) : null}
        {openNote && !openNote.corrupted && openNote.doc ? (
          <div className="flex flex-col gap-20">
            {SUMMARY_SECTION_NAMES.map((section) => (
              <div key={section}>
                <div className="mb-6 text-[13px] font-medium text-text-tertiary">
                  {section}
                </div>
                <div className="whitespace-pre-wrap text-[15px] leading-[24px] text-text-primary">
                  {openNote.doc?.sections[section]?.trim() || '—'}
                </div>
              </div>
            ))}
          </div>
        ) : null}
      </Drawer>

      <Modal
        title={
          pendingDelete?.kind === 'all'
            ? '清空全部记忆'
            : pendingDelete?.kind === 'note'
              ? '删除这场对话笔记？'
              : '删除这条记忆？'
        }
        open={pendingDelete != null}
        okText={pendingDelete?.kind === 'all' ? '清空' : '删除'}
        cancelText="取消"
        okButtonProps={{ danger: true }}
        confirmLoading={deleting}
        maskClosable={!deleting}
        closable={!deleting}
        onOk={() => void confirmPendingDelete()}
        onCancel={() => {
          if (!deleting) {
            setPendingDelete(null);
          }
        }}
      >
        {pendingDelete?.kind === 'all'
          ? '将删除所有已保存的记忆，聊天记录不会丢。'
          : '聊天记录不会丢。'}
      </Modal>
    </div>
  );
});

function MemoryGroup(props: {
  title: string;
  hint: string;
  entries: LongTermMemoryEntry[];
  disabled: boolean;
  onAdd: () => void;
  onEdit: (index: number) => void;
  onDelete: (index: number) => void;
}) {
  const { title, hint, entries, disabled, onAdd, onEdit, onDelete } = props;
  return (
    <section>
      <h2 className="m-0 mb-8 px-4 text-[13px] font-medium tracking-[0.02em] text-text-tertiary">
        {title}
      </h2>
      <div className="overflow-hidden rounded-xl bg-surface shadow-xs">
        {entries.length === 0 ? (
          <div className="px-16 py-14 text-[14px] text-text-tertiary">{hint}</div>
        ) : (
          entries.map((entry, index) => (
            <div
              key={`${entry.key}-${index}`}
              className={[
                'group flex items-center gap-8 px-4',
                index < entries.length - 1
                  ? 'border-0 border-b border-solid border-border'
                  : '',
              ].join(' ')}
            >
              <button
                type="button"
                className="min-w-0 flex-1 cursor-pointer bg-transparent px-12 py-13 text-left"
                disabled={disabled}
                onClick={() => onEdit(index)}
              >
                <span className="text-[15px] text-text-secondary">{entry.key}</span>
                <span className="text-[15px] text-text-primary">　{entry.value}</span>
              </button>
              <button
                type="button"
                aria-label={`删除 ${entry.key}`}
                data-testid="memory-entry-delete"
                className="mr-8 inline-flex size-28 shrink-0 items-center justify-center rounded-full border-0 bg-transparent text-text-tertiary hover:bg-danger-soft hover:text-danger"
                disabled={disabled}
                onClick={(event) => {
                  event.stopPropagation();
                  onDelete(index);
                }}
              >
                <DeleteOutlined />
              </button>
            </div>
          ))
        )}
        <button
          type="button"
          className={[
            'flex w-full cursor-pointer items-center gap-8 border-0 bg-transparent px-16 py-12 text-left text-[14px] text-text-secondary',
            entries.length > 0 ? 'border-t border-solid border-border' : '',
          ].join(' ')}
          disabled={disabled}
          onClick={onAdd}
        >
          <PlusOutlined className="text-[12px]" />
          添加一条
        </button>
      </div>
    </section>
  );
}

async function loadConversationTitles(): Promise<Map<string, string>> {
  const titles = new Map<string, string>();
  try {
    let page = 1;
    while (page <= 5) {
      const response = await listConversations(page, 100);
      const items = response?.items ?? [];
      for (const item of items) {
        titles.set(item.id, conversationDisplayTitle(item.title));
      }
      if (!response?.hasMore || items.length === 0) {
        break;
      }
      page += 1;
    }
  } catch {
    return titles;
  }
  return titles;
}

async function loadConversationNotes(
  index: MemoryIndexRecord[],
  readSummary: (conversationId: string) => Promise<{
    status: string;
    doc?: ConversationSummaryDoc;
    raw?: string | null;
  }>,
  titles: Map<string, string>,
): Promise<ConversationNote[]> {
  const notes: ConversationNote[] = [];
  await Promise.all(
    index.map(async (record) => {
      const conversationId = conversationIdFromSummaryPath(record.path);
      if (!conversationId) {
        return;
      }
      const title = titles.get(conversationId) ?? '未命名对话';
      const summary = await readSummary(conversationId);
      if (summary.status === 'READY' && summary.doc) {
        notes.push({
          conversationId,
          title,
          preview: summaryPreview(summary.doc),
          updatedAt: summary.doc.updatedAt || record.updatedAt,
          doc: summary.doc,
          raw: summary.raw ?? '',
          corrupted: false,
        });
        return;
      }
      if (summary.status === 'CORRUPTED') {
        notes.push({
          conversationId,
          title,
          preview: '这场笔记文件已损坏',
          updatedAt: record.updatedAt,
          doc: null,
          raw: summary.raw ?? '',
          corrupted: true,
        });
        return;
      }
      notes.push({
        conversationId,
        title,
        preview: '暂无摘要内容',
        updatedAt: record.updatedAt,
        doc: null,
        raw: '',
        corrupted: false,
      });
    }),
  );
  notes.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  return notes;
}

MemorySettingsPage.displayName = 'MemorySettingsPage';

export default MemorySettingsPage;
