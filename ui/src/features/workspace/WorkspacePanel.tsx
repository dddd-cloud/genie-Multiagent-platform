import { useEffect, useMemo, useRef, useState } from 'react';
import {
  WORKSPACE_LIMITS,
  type WorkspaceFile,
  type WorkspacePreviewKind,
} from '@/platform/workspace/types';
import { useWorkspace } from './useWorkspace';
import styles from './WorkspacePanel.module.css';

export interface WorkspacePanelProps {
  readonly className?: string;
  readonly title?: string;
  readonly subtitle?: string;
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function kindLabel(kind: WorkspacePreviewKind): string {
  return {
    text: 'TXT',
    image: 'IMG',
    pdf: 'PDF',
    office: 'DOC',
    binary: 'FILE',
  }[kind];
}

function PreviewContent({ file }: { readonly file: WorkspaceFile }) {
  const { readFile } = useWorkspace();
  const [state, setState] = useState<
    | { status: 'loading' }
    | { status: 'ready'; text?: string; objectUrl?: string }
    | { status: 'error'; message: string }
  >({ status: 'loading' });

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
          setState({
            status: 'error',
            message: '文件超过安全预览上限，请下载后使用本地应用打开',
          });
          return;
        }
        if (file.kind === 'text') {
          const previewBytes = bytes.slice(0, WORKSPACE_LIMITS.MAX_PREVIEW_BYTES);
          const text = new TextDecoder('utf-8', { fatal: false }).decode(previewBytes);
          setState({
            status: 'ready',
            text: bytes.byteLength > previewBytes.byteLength ? `${text}\n\n[预览已截断]` : text,
          });
          return;
        }
        if (file.kind === 'image' || file.kind === 'pdf') {
          objectUrl = URL.createObjectURL(new Blob([bytes], { type: file.mimeType }));
          setState({ status: 'ready', objectUrl });
          return;
        }
        setState({ status: 'ready' });
      })
      .catch((error: unknown) => {
        if (active) {
          setState({
            status: 'error',
            message: error instanceof Error ? error.message : '文件预览失败',
          });
        }
      });
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [file, readFile]);

  if (state.status === 'loading') return <div className={styles.previewLoading}>正在读取文件…</div>;
  if (state.status === 'error') return <div className={styles.previewEmpty}>{state.message}</div>;
  if (file.kind === 'text') return <pre className={styles.textPreview}>{state.text}</pre>;
  if (file.kind === 'image' && state.objectUrl) {
    return <img className={styles.mediaPreview} src={state.objectUrl} alt={file.name} />;
  }
  if (file.kind === 'pdf' && state.objectUrl) {
    return <iframe className={styles.pdfPreview} src={state.objectUrl} title={file.name} />;
  }
  return (
    <div className={styles.unsupported}>
      此格式保留在工作区中，可直接下载后使用本地应用打开。
    </div>
  );
}

function triggerDownload(file: WorkspaceFile, bytes: ArrayBuffer): void {
  const url = URL.createObjectURL(new Blob([bytes], { type: file.mimeType }));
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = file.name;
  anchor.click();
  URL.revokeObjectURL(url);
}

export function WorkspacePanel({
  className,
  title = '浏览器工作区',
  subtitle = '文件只在当前用户和工作区作用域内可见',
}: WorkspacePanelProps) {
  const {
    status,
    files,
    remoteFiles,
    error,
    remoteError,
    refresh,
    uploadFiles,
    importRemoteFile,
    readFile,
    removeFile,
    renameFile,
  } = useWorkspace();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [importing, setImporting] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const selectedFile = useMemo(
    () => files.find((file) => file.id === selectedId) ?? files[0] ?? null,
    [files, selectedId],
  );

  const acceptFiles = async (items: File[]) => {
    if (items.length === 0) return;
    setActionError(null);
    const failures = await uploadFiles(items);
    if (failures.length > 0) {
      setActionError(failures.map((failure) => `${failure.name}：${failure.message}`).join('；'));
    }
  };

  const onInputChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    await acceptFiles(Array.from(event.target.files ?? []));
    event.target.value = '';
  };

  const onDrop = async (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setDragging(false);
    await acceptFiles(Array.from(event.dataTransfer.files));
  };

  const rename = async (file: WorkspaceFile) => {
    const nextName = window.prompt('输入新的文件名', file.name);
    if (!nextName || nextName === file.name) return;
    try {
      setActionError(null);
      await renameFile(file.id, nextName);
    } catch (failure) {
      setActionError(failure instanceof Error ? failure.message : '重命名失败');
    }
  };

  const remove = async (file: WorkspaceFile) => {
    if (!window.confirm(`确定从当前工作区移除“${file.name}”吗？`)) return;
    try {
      setActionError(null);
      await removeFile(file.id);
      if (selectedId === file.id) setSelectedId(null);
    } catch (failure) {
      setActionError(failure instanceof Error ? failure.message : '移除文件失败');
    }
  };

  const download = async (file: WorkspaceFile) => {
    try {
      const bytes = await readFile(file.id);
      if (!bytes) throw new Error('文件内容不可用');
      triggerDownload(file, bytes);
    } catch (failure) {
      setActionError(failure instanceof Error ? failure.message : '下载失败');
    }
  };

  const importFile = async (file: (typeof remoteFiles)[number]) => {
    const key = `${file.requestId ?? ''}:${file.fileName}`;
    try {
      setImporting(key);
      setActionError(null);
      await importRemoteFile(file);
    } catch (failure) {
      setActionError(failure instanceof Error ? failure.message : '导入远端文件失败');
    } finally {
      setImporting(null);
    }
  };

  return (
    <section className={`${styles.panel}${className ? ` ${className}` : ''}`}>
      <header className={styles.header}>
        <div>
          <p className={styles.eyebrow}>Workspace</p>
          <h2 className={styles.title}>{title}</h2>
          <p className={styles.subtitle}>{subtitle}</p>
        </div>
        <button className={styles.refreshButton} type="button" onClick={() => void refresh()}>
          刷新
        </button>
      </header>
      <div className={styles.body}>
        <aside className={styles.sidebar}>
          <div
            className={`${styles.dropzone}${dragging ? ` ${styles.dropzoneActive}` : ''}`}
            onDragEnter={(event) => {
              event.preventDefault();
              setDragging(true);
            }}
            onDragOver={(event) => event.preventDefault()}
            onDragLeave={() => setDragging(false)}
            onDrop={(event) => void onDrop(event)}
          >
            <p className={styles.dropzoneTitle}>拖入文件到这里</p>
            <p className={styles.dropzoneHint}>支持代码、文本、图片和常见办公文件，单文件上限 25 MB。</p>
            <button className={styles.actionButton} type="button" onClick={() => inputRef.current?.click()}>
              选择文件
            </button>
            <input
              ref={inputRef}
              className={styles.hiddenInput}
              type="file"
              multiple
              onChange={(event) => void onInputChange(event)}
            />
          </div>
          <p className={styles.status}>
            {status === 'loading' && '正在加载工作区…'}
            {status === 'ready' && `${files.length} 个文件 · 仅在当前作用域保存`}
            {status === 'unavailable' && '浏览器未提供持久化存储'}
            {status === 'error' && '工作区读取失败'}
          </p>
          {(error || remoteError || actionError) && (
            <p className={styles.error}>{error || remoteError || actionError}</p>
          )}
          <ul className={styles.fileList} aria-label="工作区文件列表">
            {files.map((file) => (
              <li key={file.id}>
                <button
                  className={`${styles.fileItem}${selectedFile?.id === file.id ? ` ${styles.fileItemSelected}` : ''}`}
                  type="button"
                  onClick={() => setSelectedId(file.id)}
                >
                  <span className={styles.fileIcon}>{kindLabel(file.kind)}</span>
                  <span className={styles.fileMeta}>
                    <span className={styles.fileName}>{file.name}</span>
                    <span className={styles.fileSize}>{formatBytes(file.size)}</span>
                  </span>
                  {file.syncStatus === 'synced' && <span className={styles.badge}>已同步</span>}
                </button>
              </li>
            ))}
          </ul>
          {remoteFiles.length > 0 && (
            <div className={styles.remoteSection}>
              <p className={styles.remoteTitle}>远端待导入</p>
              {remoteFiles.map((file) => {
                const key = `${file.requestId ?? ''}:${file.fileName}`;
                return (
                  <div className={styles.remoteRow} key={key}>
                    <span className={styles.remoteName}>{file.fileName}</span>
                    <button
                      className={styles.importButton}
                      type="button"
                      disabled={importing === key}
                      onClick={() => void importFile(file)}
                    >
                      {importing === key ? '导入中' : '导入'}
                    </button>
                  </div>
                );
              })}
            </div>
          )}
        </aside>
        <main className={styles.preview}>
          {!selectedFile ? (
            <div className={styles.previewEmpty}>选择一个文件查看内容</div>
          ) : (
            <>
              <div className={styles.previewHeader}>
                <div>
                  <div className={styles.previewName}>{selectedFile.name}</div>
                  <div className={styles.fileSize}>{formatBytes(selectedFile.size)}</div>
                </div>
                <div className={styles.previewActions}>
                  <button className={styles.smallButton} type="button" onClick={() => void download(selectedFile)}>
                    下载
                  </button>
                  <button className={styles.smallButton} type="button" onClick={() => void rename(selectedFile)}>
                    重命名
                  </button>
                  <button className={styles.smallButton} type="button" onClick={() => void remove(selectedFile)}>
                    移除
                  </button>
                </div>
              </div>
              <PreviewContent file={selectedFile} />
            </>
          )}
        </main>
      </div>
    </section>
  );
}
