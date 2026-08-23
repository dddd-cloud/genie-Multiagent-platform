import { useEffect, useMemo, useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import {
  WORKSPACE_LIMITS,
  type WorkspaceFile,
  type WorkspacePreviewKind,
} from '@/platform/workspace/types';
import { useWorkspace } from './useWorkspace';
import styles from './WorkspacePanel.module.css';
import { runWorkspacePython } from './workspacePythonRuntime';

export interface WorkspacePanelProps {
  readonly className?: string;
  readonly title?: string;
  readonly subtitle?: string;
  readonly compact?: boolean;
}

type WorkspaceDialog =
  | { kind: 'create-workspace'; value: string }
  | { kind: 'rename-workspace'; value: string }
  | { kind: 'create-file'; value: string }
  | { kind: 'rename-file'; fileId: string; value: string }
  | { kind: 'delete-workspace' };

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function kindLabel(kind: WorkspacePreviewKind, name: string): string {
  if (isMarkdownFile(name)) return 'MD';
  if (name.toLowerCase().endsWith('.py')) return 'PY';
  return {
    text: 'TXT',
    image: 'IMG',
    pdf: 'PDF',
    office: 'DOC',
    binary: 'FILE',
  }[kind];
}

export function isMarkdownFile(name: string): boolean {
  return /\.(md|markdown|mdown)$/i.test(name);
}

function triggerDownload(file: WorkspaceFile, bytes: ArrayBuffer): void {
  const url = URL.createObjectURL(new Blob([bytes], { type: file.mimeType }));
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = file.name;
  anchor.click();
  URL.revokeObjectURL(url);
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

function NameDialog({
  title,
  value,
  confirmLabel,
  onChange,
  onCancel,
  onConfirm,
}: {
  readonly title: string;
  readonly value: string;
  readonly confirmLabel: string;
  readonly onChange: (value: string) => void;
  readonly onCancel: () => void;
  readonly onConfirm: () => void;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  useEffect(() => {
    inputRef.current?.focus();
    inputRef.current?.select();
  }, []);
  return (
    <div className={styles.dialogScrim} onClick={onCancel}>
      <div
        className={styles.dialog}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        data-testid="workspace-dialog"
        onClick={(event) => event.stopPropagation()}
      >
        <h3 className={styles.dialogTitle}>{title}</h3>
        <input
          ref={inputRef}
          className={styles.dialogInput}
          value={value}
          data-testid="workspace-dialog-input"
          onChange={(event) => onChange(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') onConfirm();
            if (event.key === 'Escape') onCancel();
          }}
        />
        <div className={styles.dialogActions}>
          <button className={styles.ghostButton} type="button" onClick={onCancel}>
            取消
          </button>
          <button
            className={styles.primaryButton}
            type="button"
            data-testid="workspace-dialog-confirm"
            disabled={!value.trim()}
            onClick={onConfirm}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

function ConfirmDialog({
  title,
  message,
  confirmLabel,
  onCancel,
  onConfirm,
}: {
  readonly title: string;
  readonly message: string;
  readonly confirmLabel: string;
  readonly onCancel: () => void;
  readonly onConfirm: () => void;
}) {
  return (
    <div className={styles.dialogScrim} onClick={onCancel}>
      <div
        className={styles.dialog}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        data-testid="workspace-dialog"
        onClick={(event) => event.stopPropagation()}
      >
        <h3 className={styles.dialogTitle}>{title}</h3>
        <p className={styles.dialogMessage}>{message}</p>
        <div className={styles.dialogActions}>
          <button className={styles.ghostButton} type="button" onClick={onCancel}>
            取消
          </button>
          <button
            className={styles.dangerButton}
            type="button"
            data-testid="workspace-dialog-confirm"
            onClick={onConfirm}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

export function WorkspacePanel({
  className,
  title = '工作区',
  subtitle = '文件保存在当前浏览器，仅你可见',
  compact = false,
}: WorkspacePanelProps) {
  const {
    workspaces,
    activeWorkspace,
    selectWorkspace,
    createWorkspace,
    renameWorkspace,
    deleteWorkspace,
    status,
    files,
    error,
    remoteError,
    refresh,
    uploadFiles,
    readFile,
    removeFile,
    renameFile,
    writeTextFile,
    saveRuntimeFiles,
  } = useWorkspace();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [editorText, setEditorText] = useState('');
  const [editorLoading, setEditorLoading] = useState(false);
  const [editorDirty, setEditorDirty] = useState(false);
  const [markdownPreview, setMarkdownPreview] = useState(true);
  const [running, setRunning] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [refreshHint, setRefreshHint] = useState<string | null>(null);
  const [runOutput, setRunOutput] = useState<string | null>(null);
  const [dialog, setDialog] = useState<WorkspaceDialog | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const selectedFile = useMemo(
    () => files.find((file) => file.id === selectedId) ?? files[0] ?? null,
    [files, selectedId],
  );
  const selectedIsMarkdown = selectedFile ? isMarkdownFile(selectedFile.name) : false;

  useEffect(() => {
    if (!selectedFile || selectedFile.kind !== 'text') {
      setEditorText('');
      setEditorDirty(false);
      return;
    }
    let active = true;
    setEditorLoading(true);
    setMarkdownPreview(isMarkdownFile(selectedFile.name));
    void readFile(selectedFile.id).then((bytes) => {
      if (!active) return;
      setEditorText(bytes ? new TextDecoder().decode(bytes) : '');
      setEditorDirty(false);
      setEditorLoading(false);
    }).catch((failure: unknown) => {
      if (!active) return;
      setActionError(failure instanceof Error ? failure.message : '文件读取失败');
      setEditorLoading(false);
    });
    return () => { active = false; };
  }, [readFile, selectedFile]);

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

  const saveEditor = async () => {
    if (!selectedFile || selectedFile.kind !== 'text') return;
    try {
      setActionError(null);
      await writeTextFile(selectedFile.id, selectedFile.name, editorText);
      setEditorDirty(false);
    } catch (failure) {
      setActionError(failure instanceof Error ? failure.message : '保存失败');
    }
  };

  const runPython = async () => {
    if (!selectedFile || !selectedFile.name.toLowerCase().endsWith('.py')) return;
    try {
      setRunning(true);
      setActionError(null);
      setRunOutput('正在加载浏览器 Python 环境…');
      if (editorDirty) await writeTextFile(selectedFile.id, selectedFile.name, editorText);
      const inputs = (await Promise.all(files.map(async (file) => {
        const bytes = file.id === selectedFile.id
          ? new TextEncoder().encode(editorText).buffer as ArrayBuffer
          : await readFile(file.id);
        return bytes ? [{ name: file.name, mimeType: file.mimeType, bytes }] : [];
      }))).flat();
      const result = await runWorkspacePython(selectedFile.name, inputs);
      await saveRuntimeFiles(result.files);
      const text = [result.stdout, result.stderr, result.error].filter(Boolean).join('\n');
      setRunOutput(text || (result.success ? '运行完成（无输出）' : '运行失败'));
      setEditorDirty(false);
    } catch (failure) {
      setRunOutput(null);
      setActionError(failure instanceof Error ? failure.message : '浏览器 Python 执行失败');
    } finally {
      setRunning(false);
    }
  };

  const refreshFiles = async () => {
    setRefreshing(true);
    setActionError(null);
    try {
      await refresh();
      setRefreshHint('已刷新');
      window.setTimeout(() => setRefreshHint(null), 1600);
    } catch (failure) {
      setActionError(failure instanceof Error ? failure.message : '刷新失败');
    } finally {
      setRefreshing(false);
    }
  };

  const submitDialog = async () => {
    if (!dialog) return;
    try {
      setActionError(null);
      if (dialog.kind === 'delete-workspace') {
        await deleteWorkspace();
        setSelectedId(null);
        setDialog(null);
        return;
      }
      const value = dialog.value.trim();
      if (!value) return;
      if (dialog.kind === 'create-workspace') {
        createWorkspace(value);
      } else if (dialog.kind === 'rename-workspace') {
        renameWorkspace(value);
      } else if (dialog.kind === 'create-file') {
        const file = await writeTextFile(undefined, value, selectedIsMarkdownPlaceholder(value));
        setSelectedId(file.id);
      } else {
        await renameFile(dialog.fileId, value);
      }
      setDialog(null);
    } catch (failure) {
      setActionError(failure instanceof Error ? failure.message : '操作失败');
    }
  };

  return (
    <section className={`${styles.panel}${compact ? ` ${styles.compact}` : ''}${className ? ` ${className}` : ''}`}>
      <header className={styles.header}>
        <div className={styles.headerCopy}>
          <h2 className={styles.title}>{title}</h2>
          <p className={styles.subtitle}>{subtitle}</p>
        </div>
        <div className={styles.workspaceActions}>
          <select
            className={styles.workspaceSelect}
            value={activeWorkspace.id}
            onChange={(event) => selectWorkspace(event.target.value)}
            aria-label="选择工作区"
            data-testid="workspace-select"
          >
            {workspaces.map((workspace) => (
              <option key={workspace.id} value={workspace.id}>{workspace.name}</option>
            ))}
          </select>
          <button
            className={styles.toolbarButton}
            type="button"
            data-testid="workspace-create"
            onClick={() => setDialog({
              kind: 'create-workspace',
              value: `工作区 ${workspaces.length + 1}`,
            })}
          >
            新建
          </button>
          <button
            className={styles.toolbarButton}
            type="button"
            data-testid="workspace-rename"
            onClick={() => setDialog({
              kind: 'rename-workspace',
              value: activeWorkspace.name,
            })}
          >
            重命名
          </button>
          <button
            className={styles.toolbarButton}
            type="button"
            data-testid="workspace-delete"
            disabled={workspaces.length <= 1}
            onClick={() => setDialog({ kind: 'delete-workspace' })}
          >
            删除
          </button>
          <button
            className={styles.toolbarButton}
            type="button"
            data-testid="workspace-refresh"
            disabled={refreshing}
            onClick={() => void refreshFiles()}
          >
            {refreshing ? '刷新中' : refreshHint ?? '刷新'}
          </button>
        </div>
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
            <div className={styles.dropzoneCopy}>
              <p className={styles.dropzoneTitle}>添加文件</p>
              <p className={styles.dropzoneHint}>拖入，或从本地选择。单文件上限 25 MB。</p>
            </div>
            <div className={styles.dropzoneActions}>
              <button
                className={styles.secondaryButton}
                type="button"
                data-testid="workspace-create-file"
                onClick={() => setDialog({ kind: 'create-file', value: '未命名.md' })}
              >
                新建文件
              </button>
              <button className={styles.primaryButton} type="button" onClick={() => inputRef.current?.click()}>
                {compact ? '上传' : '选择文件'}
              </button>
            </div>
            <input
              ref={inputRef}
              className={styles.hiddenInput}
              type="file"
              multiple
              onChange={(event) => void onInputChange(event)}
            />
          </div>
          <p className={styles.status}>
            {status === 'loading' && '正在加载…'}
            {status === 'ready' && `${files.length} 个文件 · 保存在此浏览器`}
            {status === 'unavailable' && '当前浏览器未提供持久化存储'}
            {status === 'error' && '工作区读取失败'}
          </p>
          {(error || remoteError || actionError) && (
            <p className={styles.error}>{error || remoteError || actionError}</p>
          )}
          <ul className={styles.fileList} aria-label="工作区文件列表">
            {files.length === 0 && status === 'ready' ? (
              <li className={styles.emptyList}>还没有文件</li>
            ) : null}
            {files.map((file) => (
              <li key={file.id}>
                <button
                  className={`${styles.fileItem}${selectedFile?.id === file.id ? ` ${styles.fileItemSelected}` : ''}`}
                  type="button"
                  onClick={() => setSelectedId(file.id)}
                >
                  <span className={styles.fileIcon}>{kindLabel(file.kind, file.name)}</span>
                  <span className={styles.fileMeta}>
                    <span className={styles.fileName}>{file.name}</span>
                    <span className={styles.fileSize}>{formatBytes(file.size)}</span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </aside>
        <main className={styles.preview}>
          {!selectedFile ? (
            <div className={styles.previewEmpty}>选择一个文件查看内容</div>
          ) : (
            <>
              <div className={styles.previewHeader}>
                <div className={styles.previewIdentity}>
                  <div className={styles.previewName}>{selectedFile.name}</div>
                  <div className={styles.fileSize}>{formatBytes(selectedFile.size)}</div>
                </div>
                <div className={styles.previewActions}>
                  {selectedIsMarkdown && selectedFile.kind === 'text' ? (
                    <button
                      className={styles.toolbarButton}
                      type="button"
                      data-testid="workspace-markdown-toggle"
                      onClick={() => setMarkdownPreview((value) => !value)}
                    >
                      {markdownPreview ? '编辑' : '预览'}
                    </button>
                  ) : null}
                  <button className={styles.toolbarButton} type="button" onClick={() => void download(selectedFile)}>
                    下载
                  </button>
                  {selectedFile.kind === 'text' && (
                    <button
                      className={styles.toolbarButton}
                      type="button"
                      disabled={!editorDirty}
                      onClick={() => void saveEditor()}
                    >
                      保存
                    </button>
                  )}
                  {selectedFile.name.toLowerCase().endsWith('.py') && (
                    <button className={styles.primaryButton} type="button" disabled={running} onClick={() => void runPython()}>
                      {running ? '运行中…' : '运行'}
                    </button>
                  )}
                  <button
                    className={styles.toolbarButton}
                    type="button"
                    onClick={() => setDialog({
                      kind: 'rename-file',
                      fileId: selectedFile.id,
                      value: selectedFile.name,
                    })}
                  >
                    重命名
                  </button>
                  <button className={styles.toolbarButton} type="button" onClick={() => void remove(selectedFile)}>
                    删除
                  </button>
                </div>
              </div>
              {selectedFile.kind === 'text' ? (
                <div className={styles.editorArea}>
                  {editorLoading ? (
                    <div className={styles.previewLoading}>正在读取文件…</div>
                  ) : selectedIsMarkdown && markdownPreview ? (
                    <div className={styles.markdown} data-testid="workspace-markdown-preview">
                      {editorText.trim() ? (
                        <ReactMarkdown remarkPlugins={[remarkGfm]} skipHtml>
                          {editorText}
                        </ReactMarkdown>
                      ) : (
                        <p className={styles.markdownEmpty}>空白 Markdown 文件</p>
                      )}
                    </div>
                  ) : (
                    <textarea
                      className={styles.codeEditor}
                      value={editorText}
                      spellCheck={false}
                      onChange={(event) => { setEditorText(event.target.value); setEditorDirty(true); }}
                      aria-label={`编辑 ${selectedFile.name}`}
                    />
                  )}
                  {runOutput !== null && <pre className={styles.runOutput}>{runOutput}</pre>}
                </div>
              ) : <PreviewContent file={selectedFile} />}
            </>
          )}
        </main>
      </div>
      {dialog?.kind === 'delete-workspace' ? (
        <ConfirmDialog
          title="删除工作区"
          message={`删除“${activeWorkspace.name}”及其全部文件？此操作无法撤销。`}
          confirmLabel="删除"
          onCancel={() => setDialog(null)}
          onConfirm={() => void submitDialog()}
        />
      ) : dialog ? (
        <NameDialog
          title={
            dialog.kind === 'create-workspace'
              ? '新建工作区'
              : dialog.kind === 'rename-workspace'
                ? '重命名工作区'
                : dialog.kind === 'create-file'
                  ? '新建文件'
                  : '重命名文件'
          }
          value={dialog.value}
          confirmLabel={dialog.kind.startsWith('create') ? '创建' : '保存'}
          onChange={(value) => setDialog({ ...dialog, value })}
          onCancel={() => setDialog(null)}
          onConfirm={() => void submitDialog()}
        />
      ) : null}
    </section>
  );
}

function selectedIsMarkdownPlaceholder(name: string): string {
  return isMarkdownFile(name) ? '# 未命名\n\n' : '';
}
