import { useEffect, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Modal, Button } from 'antd';
import { CloseOutlined, DownloadOutlined, PlayCircleOutlined } from '@ant-design/icons';
import { WORKSPACE_LIMITS, type WorkspaceFile } from '@/platform/workspace/types';
import { isMarkdownFile } from './WorkspacePanel';

export interface WorkspaceFilePreviewModalProps {
  readonly file: WorkspaceFile | null;
  readonly onClose: () => void;
  readonly readFile: (fileId: string) => Promise<ArrayBuffer | null>;
  readonly onRunPython?: (file: WorkspaceFile) => void;
  readonly running?: boolean;
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function triggerDownload(file: WorkspaceFile, bytes: ArrayBuffer): void {
  const url = URL.createObjectURL(new Blob([bytes], { type: file.mimeType }));
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = file.name;
  anchor.click();
  URL.revokeObjectURL(url);
}

type PreviewState =
  | { status: 'loading' }
  | { status: 'ready'; bytes: ArrayBuffer; text?: string; objectUrl?: string }
  | { status: 'error'; message: string };

function PreviewBody({
  file,
  readFile,
}: {
  readonly file: WorkspaceFile;
  readonly readFile: (fileId: string) => Promise<ArrayBuffer | null>;
}) {
  const [state, setState] = useState<PreviewState>({ status: 'loading' });

  useEffect(() => {
    let active = true;
    let objectUrl: string | undefined;
    setState({ status: 'loading' });
    void readFile(file.id)
      .then((bytes) => {
        if (!active) return;
        if (!bytes) {
          setState({ status: 'error', message: '文件内容不可用' });
          return;
        }
        if ((file.kind === 'image' || file.kind === 'pdf') && file.size > WORKSPACE_LIMITS.MAX_PREVIEW_BYTES) {
          setState({ status: 'error', message: '文件超过安全预览上限，请下载后使用本地应用打开' });
          return;
        }
        if (file.kind === 'text') {
          const previewBytes = bytes.slice(0, WORKSPACE_LIMITS.MAX_PREVIEW_BYTES);
          const text = new TextDecoder('utf-8', { fatal: false }).decode(previewBytes);
          setState({
            status: 'ready',
            bytes,
            text: bytes.byteLength > previewBytes.byteLength ? `${text}\n\n[预览已截断]` : text,
          });
          return;
        }
        if (file.kind === 'image' || file.kind === 'pdf') {
          objectUrl = URL.createObjectURL(new Blob([bytes], { type: file.mimeType }));
          setState({ status: 'ready', bytes, objectUrl });
          return;
        }
        setState({ status: 'ready', bytes });
      })
      .catch((error: unknown) => {
        if (active) {
          setState({ status: 'error', message: error instanceof Error ? error.message : '文件预览失败' });
        }
      });
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [file, readFile]);

  if (state.status === 'loading') {
    return <div className="flex h-full items-center justify-center text-text-secondary">正在读取文件…</div>;
  }
  if (state.status === 'error') {
    return <div className="flex h-full items-center justify-center text-text-secondary">{state.message}</div>;
  }
  if (file.kind === 'text' && isMarkdownFile(file.name)) {
    return (
      <div className="prose prose-sm max-w-none px-24 py-16">
        {state.text?.trim() ? (
          <ReactMarkdown remarkPlugins={[remarkGfm]} skipHtml>
            {state.text}
          </ReactMarkdown>
        ) : (
          <p className="text-text-secondary">空白 Markdown 文件</p>
        )}
      </div>
    );
  }
  if (file.kind === 'text') {
    return (
      <pre className="h-full overflow-auto whitespace-pre-wrap break-words px-24 py-16 text-[13px] leading-[20px] text-text-primary">
        {state.text}
      </pre>
    );
  }
  if (file.kind === 'image' && state.objectUrl) {
    return (
      <div className="flex h-full items-center justify-center overflow-auto p-16">
        <img className="max-h-full max-w-full object-contain" src={state.objectUrl} alt={file.name} />
      </div>
    );
  }
  if (file.kind === 'pdf' && state.objectUrl) {
    return <iframe className="h-full w-full border-0" src={state.objectUrl} title={file.name} />;
  }
  return (
    <div className="flex h-full items-center justify-center text-text-secondary">
      此格式保留在工作区中，可下载后使用本地应用打开。
    </div>
  );
}

export function WorkspaceFilePreviewModal({
  file,
  onClose,
  readFile,
  onRunPython,
  running = false,
}: WorkspaceFilePreviewModalProps) {
  const isPython = file?.name.toLowerCase().endsWith('.py') ?? false;

  return (
    <Modal
      open={!!file}
      onCancel={onClose}
      footer={null}
      closable={false}
      maskClosable
      centered
      width={960}
      destroyOnHidden
      data-testid="workspace-file-preview-modal"
      className="settings-modal"
      styles={{
        content: { padding: 0, borderRadius: 16, overflow: 'hidden' },
        body: { padding: 0 },
      }}
    >
      {file ? (
        <div className="flex h-[min(80vh,720px)] min-h-[480px] flex-col bg-surface">
          <div className="flex shrink-0 items-center justify-between gap-8 border-b border-border px-16 py-10">
            <div className="flex min-w-0 flex-col">
              <span className="truncate text-[14px] font-medium text-text-primary">{file.name}</span>
              <span className="text-[12px] text-text-secondary">{formatBytes(file.size)}</span>
            </div>
            <div className="flex shrink-0 items-center gap-8">
              {isPython && onRunPython ? (
                <Button
                  size="small"
                  icon={<PlayCircleOutlined />}
                  loading={running}
                  onClick={() => onRunPython(file)}
                >
                  运行
                </Button>
              ) : null}
              <Button
                size="small"
                icon={<DownloadOutlined />}
                onClick={() => {
                  void readFile(file.id).then((bytes) => {
                    if (bytes) triggerDownload(file, bytes);
                  });
                }}
              >
                下载
              </Button>
              <button
                type="button"
                aria-label="关闭"
                data-testid="workspace-file-preview-close"
                onClick={onClose}
                className="flex h-28 w-28 items-center justify-center rounded-[8px] text-text-secondary transition-colors hover:bg-[#F5F5F7]"
              >
                <CloseOutlined className="text-[14px]" />
              </button>
            </div>
          </div>
          <div className="min-h-0 flex-1 overflow-auto">
            <PreviewBody file={file} readFile={readFile} />
          </div>
        </div>
      ) : null}
    </Modal>
  );
}

export default WorkspaceFilePreviewModal;
