import { memo, useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Input,
  Modal,
  Space,
  Typography,
  message,
} from 'antd';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { getMessages } from '@/features/conversation/api';
import {
  emptyLongTermMemoryDoc,
  serializeLongTermMemory,
} from './markdownSerializer';
import { parseLongTermMemory } from './markdownParser';
import { useLocalMemory } from './useLocalMemory';
import type { MemoryIndexRecord, MemoryTaskRecord } from './types';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

const MemorySettingsPage: GenieType.FC = memo(() => {
  const memory = useLocalMemory();
  const [editorText, setEditorText] = useState('');
  const [loadError, setLoadError] = useState<string | null>(null);
  const [summaries, setSummaries] = useState<MemoryIndexRecord[]>([]);
  const [tasks, setTasks] = useState<MemoryTaskRecord[]>([]);
  const [busy, setBusy] = useState(false);

  const reload = useCallback(async () => {
    setBusy(true);
    setLoadError(null);
    try {
      await memory.refreshStatus();
      const repo = memory.repository;
      if (!repo) {
        setEditorText('');
        setSummaries([]);
        setTasks([]);
        return;
      }
      const ltm = await repo.readLongTermMemory();
      if (ltm.status === 'READY') {
        setEditorText(ltm.raw);
      } else if (ltm.status === 'EMPTY') {
        setEditorText(serializeLongTermMemory(emptyLongTermMemoryDoc()));
      } else if (ltm.status === 'CORRUPTED') {
        setEditorText(ltm.raw);
        setLoadError(`长期记忆文件损坏：${ltm.reason}`);
      } else if (ltm.status === 'UNAVAILABLE') {
        setEditorText('');
        setLoadError('当前浏览器不支持 OPFS，本地记忆不可用。');
      } else {
        setEditorText('');
        setLoadError(ltm.message);
      }
      setSummaries(await memory.listSummaryIndex());
      setTasks(await memory.listTasks());
    } finally {
      setBusy(false);
    }
  }, [memory]);

  useEffect(() => {
    void reload();
  }, [memory.userId]);

  const taskCounts = useMemo(() => {
    const counts = {
      PENDING: 0,
      RETRY: 0,
      FAILED: 0,
      RUNNING: 0,
      DONE: 0
    };
    for (const task of tasks) {
      counts[task.status] += 1;
    }
    return counts;
  }, [tasks]);

  const previewSafe = useMemo(() => {
    // Preview only — never render raw HTML plugins.
    return editorText;
  }, [editorText]);

  const saveEditor = async () => {
    const repo = memory.repository;
    if (!repo) {
      return;
    }
    const parsed = parseLongTermMemory(editorText);
    if (!parsed.ok) {
      message.error(`无法保存：${parsed.reason}`);
      return;
    }
    setBusy(true);
    try {
      await repo.writeLongTermMemory({
        ...parsed.doc,
        updatedAt: new Date().toISOString(),
      });
      message.success('长期记忆已保存');
      await reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setBusy(false);
    }
  };

  const confirmRebuildLtm = () => {
    Modal.confirm({
      title: '重建长期记忆',
      content: '将用空的合法四章节模板覆盖当前长期记忆文件。是否继续？',
      okText: '重建',
      cancelText: '取消',
      onOk: async () => {
        await memory.rebuildLongTermMemory();
        message.success('已重建长期记忆模板');
        await reload();
      },
    });
  };

  const confirmClearLtm = () => {
    Modal.confirm({
      title: '清空长期记忆',
      content: '将删除当前用户的长期记忆文件与索引。是否继续？',
      okText: '清空',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await memory.clearLongTermMemory();
        message.success('已清空长期记忆');
        await reload();
      },
    });
  };

  const confirmClearSummary = (record: MemoryIndexRecord) => {
    const match = /\/conversations\/([^/]+)\//.exec(record.path);
    const conversationId = match?.[1];
    if (!conversationId) {
      return;
    }
    Modal.confirm({
      title: '清空会话摘要',
      content: `将删除会话 ${conversationId} 的摘要文件。是否继续？`,
      okText: '清空',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await memory.clearConversationSummary(conversationId);
        message.success('已清空摘要');
        await reload();
      },
    });
  };

  const confirmRebuildSummary = (record: MemoryIndexRecord) => {
    const match = /\/conversations\/([^/]+)\//.exec(record.path);
    const conversationId = match?.[1];
    if (!conversationId) {
      return;
    }
    Modal.confirm({
      title: '重建会话摘要',
      content: '将重新读取服务端已完成轮次并入队摘要任务。是否继续？',
      okText: '重建',
      cancelText: '取消',
      onOk: async () => {
        const messages = (await getMessages(conversationId)) ?? [];
        await memory.rebuildConversationSummary(conversationId, messages);
        message.success('已入队摘要重建任务');
        await reload();
      },
    });
  };

  const exportLtm = () => {
    memory.exportTextFile('长期记忆.md', editorText);
  };

  const exportSummary = async (record: MemoryIndexRecord) => {
    const match = /\/conversations\/([^/]+)\//.exec(record.path);
    const conversationId = match?.[1];
    if (!conversationId || !memory.repository) {
      return;
    }
    const summary =
      await memory.repository.readConversationSummary(conversationId);
    if (summary.status === 'READY' || summary.status === 'CORRUPTED') {
      memory.exportTextFile(`${conversationId}-对话摘要.md`, summary.raw);
      return;
    }
    message.warning('摘要文件不可导出');
  };

  return (
    <div className="p-24 max-w-[960px]">
      <Title level={3}>本地记忆</Title>
      <Paragraph type="secondary">
        按当前登录用户隔离的浏览器本地记忆（OPFS）。退出登录不会删除文件。
      </Paragraph>

      <Space direction="vertical" size="middle" className="w-full">
        <Alert
          type={
            memory.opfsStatus === 'UNAVAILABLE' ||
            memory.opfsStatus === 'CORRUPTED' ||
            memory.opfsStatus === 'ERROR'
              ? 'warning'
              : 'info'
          }
          showIcon
          message={`OPFS 状态：${memory.opfsStatus}`}
          description={`当前 userId 作用域：${memory.userId}`}
        />

        {loadError ? <Alert type="error" showIcon message={loadError} /> : null}

        <div>
          <Title level={5}>任务队列</Title>
          <Text>
            PENDING {taskCounts.PENDING} / RETRY {taskCounts.RETRY} / FAILED{' '}
            {taskCounts.FAILED}
          </Text>
          <div className="mt-8">
            <Button
              disabled={busy || taskCounts.FAILED === 0}
              onClick={() => {
                void memory.retryFailedTasks().then(() => reload());
              }}
            >
              重试失败任务
            </Button>
          </div>
        </div>

        <div>
          <Title level={5}>长期记忆</Title>
          <TextArea
            value={editorText}
            onChange={(e) => setEditorText(e.target.value)}
            autoSize={{
              minRows: 12,
              maxRows: 24
            }}
            disabled={busy || memory.opfsStatus === 'UNAVAILABLE'}
          />
          <Space className="mt-12" wrap>
            <Button type="primary" loading={busy} onClick={() => void saveEditor()}>
              保存
            </Button>
            <Button onClick={confirmRebuildLtm}>重建</Button>
            <Button danger onClick={confirmClearLtm}>
              清空
            </Button>
            <Button onClick={exportLtm}>导出</Button>
            <Button onClick={() => void reload()}>刷新</Button>
          </Space>
          <div className="mt-16 prose max-w-none">
            <Title level={5}>预览</Title>
            <ReactMarkdown remarkPlugins={[remarkGfm]} skipHtml>
              {previewSafe}
            </ReactMarkdown>
          </div>
        </div>

        <div>
          <Title level={5}>会话摘要</Title>
          {summaries.length === 0 ? (
            <Text type="secondary">暂无摘要索引</Text>
          ) : (
            <Space direction="vertical" className="w-full">
              {summaries.map((record) => (
                <div
                  key={record.path}
                  className="flex flex-wrap items-center justify-between gap-8 py-8"
                >
                  <div>
                    <div>
                      <Text code>{record.path}</Text>
                    </div>
                    <Text type="secondary">
                      lastSummarizedTurnNo:{' '}
                      {record.lastSummarizedTurnNo ?? '—'} · updatedAt:{' '}
                      {record.updatedAt}
                    </Text>
                  </div>
                  <Space wrap>
                    <Button
                      size="small"
                      onClick={() => confirmRebuildSummary(record)}
                    >
                      重建
                    </Button>
                    <Button
                      size="small"
                      danger
                      onClick={() => confirmClearSummary(record)}
                    >
                      清空
                    </Button>
                    <Button
                      size="small"
                      onClick={() => void exportSummary(record)}
                    >
                      导出
                    </Button>
                  </Space>
                </div>
              ))}
            </Space>
          )}
        </div>
      </Space>
    </div>
  );
});

MemorySettingsPage.displayName = 'MemorySettingsPage';

export default MemorySettingsPage;
