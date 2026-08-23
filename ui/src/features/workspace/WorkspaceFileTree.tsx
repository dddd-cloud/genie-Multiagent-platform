import { useMemo, useState } from 'react';
import {
  DownOutlined,
  FileOutlined,
  FolderOpenOutlined,
  FolderOutlined,
  PlusOutlined,
  RightOutlined,
} from '@ant-design/icons';
import { Dropdown, Input, message, type MenuProps } from 'antd';
import type { WorkspaceFile, WorkspaceFolder } from '@/platform/workspace/types';
import type { WorkspaceFilesState } from './useWorkspaceFiles';

const DRAG_MIME = 'application/x-joyagent-workspace-item';

interface DragPayload {
  readonly kind: 'file' | 'folder';
  readonly id: string;
}

interface TreeNode {
  readonly folder: WorkspaceFolder | null; // null = workspace root
  readonly childFolders: WorkspaceFolder[];
  readonly childFiles: WorkspaceFile[];
}

function buildTree(files: readonly WorkspaceFile[], folders: readonly WorkspaceFolder[]): Map<string | null, TreeNode> {
  const byParent = new Map<string | null, TreeNode>();
  const ensure = (parentId: string | null): TreeNode => {
    let node = byParent.get(parentId);
    if (!node) {
      node = { folder: null, childFolders: [], childFiles: [] };
      byParent.set(parentId, node);
    }
    return node;
  };
  ensure(null);
  for (const folder of folders) {
    ensure(folder.parentId).childFolders.push(folder);
    if (!byParent.has(folder.id)) {
      byParent.set(folder.id, { folder, childFolders: [], childFiles: [] });
    } else {
      const existing = byParent.get(folder.id)!;
      byParent.set(folder.id, { ...existing, folder });
    }
  }
  for (const file of files) {
    ensure(file.parentId).childFiles.push(file);
  }
  return byParent;
}

function kindLabel(file: WorkspaceFile): string {
  if (/\.(md|markdown|mdown)$/i.test(file.name)) return 'MD';
  if (file.name.toLowerCase().endsWith('.py')) return 'PY';
  return { text: 'TXT', image: 'IMG', pdf: 'PDF', office: 'DOC', binary: 'FILE' }[file.kind];
}

export interface WorkspaceFileTreeProps {
  readonly workspaceState: WorkspaceFilesState;
  readonly onOpenFile: (file: WorkspaceFile) => void;
  readonly selectedFileId?: string | null;
  readonly readOnly?: boolean;
}

export function WorkspaceFileTree({
  workspaceState,
  onOpenFile,
  selectedFileId,
  readOnly = false,
}: WorkspaceFileTreeProps) {
  const { files, folders, uploadFiles, removeFile, renameFile, moveFile, createFolder, renameFolder, moveFolder, deleteFolder } =
    workspaceState;
  const tree = useMemo(() => buildTree(files, folders), [files, folders]);
  const [expanded, setExpanded] = useState<Set<string>>(() => new Set());
  const [renaming, setRenaming] = useState<{ kind: 'file' | 'folder'; id: string; value: string } | null>(null);
  const [creating, setCreating] = useState<{ parentId: string | null; kind: 'file' | 'folder'; value: string } | null>(null);
  const [dragOverId, setDragOverId] = useState<string | null>(null);

  const toggle = (folderId: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(folderId)) next.delete(folderId);
      else next.add(folderId);
      return next;
    });
  };

  const acceptDrop = async (event: React.DragEvent, parentId: string | null) => {
    event.preventDefault();
    event.stopPropagation();
    setDragOverId(null);
    const internal = event.dataTransfer.getData(DRAG_MIME);
    if (internal) {
      try {
        const payload = JSON.parse(internal) as DragPayload;
        if (payload.kind === 'file') await moveFile(payload.id, parentId);
        else await moveFolder(payload.id, parentId);
      } catch (error) {
        message.error(error instanceof Error ? error.message : '移动失败');
      }
      return;
    }
    const droppedFiles = Array.from(event.dataTransfer.files ?? []);
    if (droppedFiles.length === 0) return;
    const failures = await uploadFiles(droppedFiles, parentId);
    if (failures.length) message.warning(failures.join('；'));
  };

  const submitCreate = async () => {
    if (!creating) return;
    const value = creating.value.trim();
    if (!value) {
      setCreating(null);
      return;
    }
    try {
      if (creating.kind === 'folder') {
        await createFolder(value, creating.parentId);
      } else {
        const file = new File([''], value, { type: 'text/plain' });
        const failures = await uploadFiles([file], creating.parentId);
        if (failures.length) message.error(failures.join('；'));
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '创建失败');
    }
    setCreating(null);
  };

  const submitRename = async () => {
    if (!renaming) return;
    const value = renaming.value.trim();
    if (!value) {
      setRenaming(null);
      return;
    }
    try {
      if (renaming.kind === 'file') await renameFile(renaming.id, value);
      else await renameFolder(renaming.id, value);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '重命名失败');
    }
    setRenaming(null);
  };

  const folderMenu = (folder: WorkspaceFolder): MenuProps['items'] => [
    { key: 'new-file', label: '新建文件', onClick: () => setCreating({ parentId: folder.id, kind: 'file', value: '未命名.md' }) },
    { key: 'new-folder', label: '新建文件夹', onClick: () => setCreating({ parentId: folder.id, kind: 'folder', value: '新建文件夹' }) },
    { key: 'rename', label: '重命名', onClick: () => setRenaming({ kind: 'folder', id: folder.id, value: folder.name }) },
    {
      key: 'delete',
      label: '删除',
      danger: true,
      onClick: () => {
        Modal_confirmDeleteFolder(folder.name, () => deleteFolder(folder.id));
      },
    },
  ];

  const fileMenu = (file: WorkspaceFile): MenuProps['items'] => [
    { key: 'open', label: '预览', onClick: () => onOpenFile(file) },
    { key: 'rename', label: '重命名', onClick: () => setRenaming({ kind: 'file', id: file.id, value: file.name }) },
    {
      key: 'delete',
      label: '删除',
      danger: true,
      onClick: () => {
        void removeFile(file.id).catch((error: unknown) => {
          message.error(error instanceof Error ? error.message : '删除失败');
        });
      },
    },
  ];

  function renderFolder(folder: WorkspaceFolder, depth: number) {
    const node = tree.get(folder.id) ?? { folder, childFolders: [], childFiles: [] };
    const isOpen = expanded.has(folder.id);
    const isDragOver = dragOverId === folder.id;
    return (
      <div key={folder.id}>
        <div
          className={`group flex items-center gap-4 rounded-[6px] px-4 py-4 text-[13px] hover:bg-[#F5F5F7] ${isDragOver ? 'bg-[#EFF3FF]' : ''}`}
          style={{ paddingLeft: 8 + depth * 16 }}
          draggable={!readOnly}
          onDragStart={(event) => {
            event.dataTransfer.setData(DRAG_MIME, JSON.stringify({ kind: 'folder', id: folder.id } satisfies DragPayload));
          }}
          onDragOver={(event) => {
            event.preventDefault();
            setDragOverId(folder.id);
          }}
          onDragLeave={() => setDragOverId((current) => (current === folder.id ? null : current))}
          onDrop={(event) => void acceptDrop(event, folder.id)}
        >
          <button type="button" className="flex h-16 w-16 items-center justify-center text-text-secondary" onClick={() => toggle(folder.id)}>
            {isOpen ? <DownOutlined className="text-[10px]" /> : <RightOutlined className="text-[10px]" />}
          </button>
          {isOpen ? <FolderOpenOutlined className="text-text-secondary" /> : <FolderOutlined className="text-text-secondary" />}
          {renaming?.kind === 'folder' && renaming.id === folder.id ? (
            <Input
              autoFocus
              size="small"
              value={renaming.value}
              onChange={(event) => setRenaming({ ...renaming, value: event.target.value })}
              onPressEnter={() => void submitRename()}
              onBlur={() => void submitRename()}
            />
          ) : (
            <span className="min-w-0 flex-1 truncate" onClick={() => toggle(folder.id)}>{folder.name}</span>
          )}
          {!readOnly && (
            <Dropdown menu={{ items: folderMenu(folder) }} trigger={['click']}>
              <button
                type="button"
                className="invisible flex h-20 w-20 shrink-0 items-center justify-center rounded-[4px] text-text-secondary hover:bg-[#E9E9EC] group-hover:visible"
                onClick={(event) => event.stopPropagation()}
              >
                ⋯
              </button>
            </Dropdown>
          )}
        </div>
        {isOpen ? (
          <div>
            {creating?.parentId === folder.id ? renderCreateInput(depth + 1) : null}
            {node.childFolders
              .slice()
              .sort((a, b) => a.name.localeCompare(b.name))
              .map((child) => renderFolder(child, depth + 1))}
            {node.childFiles
              .slice()
              .sort((a, b) => a.name.localeCompare(b.name))
              .map((file) => renderFile(file, depth + 1))}
          </div>
        ) : null}
      </div>
    );
  }

  function renderFile(file: WorkspaceFile, depth: number) {
    const isRenaming = renaming?.kind === 'file' && renaming.id === file.id;
    const isSelected = selectedFileId === file.id;
    return (
      <div
        key={file.id}
        className={`group flex items-center gap-4 rounded-[6px] px-4 py-4 text-[13px] hover:bg-[#F5F5F7] ${isSelected ? 'bg-[#EFF3FF]' : ''}`}
        style={{ paddingLeft: 8 + depth * 16 + 16 }}
        draggable={!readOnly}
        onDragStart={(event) => {
          event.dataTransfer.setData(DRAG_MIME, JSON.stringify({ kind: 'file', id: file.id } satisfies DragPayload));
        }}
        onClick={() => onOpenFile(file)}
      >
        <FileOutlined className="text-text-secondary" />
        <span className="shrink-0 rounded-[3px] bg-[#F0F0F2] px-4 text-[10px] text-text-secondary">{kindLabel(file)}</span>
        {isRenaming ? (
          <Input
            autoFocus
            size="small"
            value={renaming.value}
            onChange={(event) => setRenaming({ ...renaming, value: event.target.value })}
            onPressEnter={() => void submitRename()}
            onBlur={() => void submitRename()}
            onClick={(event) => event.stopPropagation()}
          />
        ) : (
          <span className="min-w-0 flex-1 truncate">{file.name}</span>
        )}
        {!readOnly && (
          <Dropdown menu={{ items: fileMenu(file) }} trigger={['click']}>
            <button
              type="button"
              className="invisible flex h-20 w-20 shrink-0 items-center justify-center rounded-[4px] text-text-secondary hover:bg-[#E9E9EC] group-hover:visible"
              onClick={(event) => event.stopPropagation()}
            >
              ⋯
            </button>
          </Dropdown>
        )}
      </div>
    );
  }

  function renderCreateInput(depth: number) {
    if (!creating) return null;
    return (
      <div className="flex items-center gap-4 px-4 py-4 text-[13px]" style={{ paddingLeft: 8 + depth * 16 }}>
        {creating.kind === 'folder' ? <FolderOutlined className="text-text-secondary" /> : <FileOutlined className="text-text-secondary" />}
        <Input
          autoFocus
          size="small"
          value={creating.value}
          onChange={(event) => setCreating({ ...creating, value: event.target.value })}
          onPressEnter={() => void submitCreate()}
          onBlur={() => void submitCreate()}
        />
      </div>
    );
  }

  const root = tree.get(null) ?? { folder: null, childFolders: [], childFiles: [] };
  const rootDragOver = dragOverId === 'root';

  return (
    <div
      className={`rounded-[8px] ${rootDragOver ? 'bg-[#EFF3FF]' : ''}`}
      onDragOver={(event) => {
        event.preventDefault();
        setDragOverId('root');
      }}
      onDragLeave={() => setDragOverId((current) => (current === 'root' ? null : current))}
      onDrop={(event) => void acceptDrop(event, null)}
    >
      {!readOnly && (
        <div className="flex items-center gap-8 px-4 pb-4">
          <button
            type="button"
            className="flex items-center gap-4 rounded-[6px] px-6 py-2 text-[12px] text-text-secondary hover:bg-[#F5F5F7]"
            onClick={() => setCreating({ parentId: null, kind: 'folder', value: '新建文件夹' })}
          >
            <PlusOutlined /> 文件夹
          </button>
          <button
            type="button"
            className="flex items-center gap-4 rounded-[6px] px-6 py-2 text-[12px] text-text-secondary hover:bg-[#F5F5F7]"
            onClick={() => setCreating({ parentId: null, kind: 'file', value: '未命名.md' })}
          >
            <PlusOutlined /> 文件
          </button>
        </div>
      )}
      {creating?.parentId === null ? renderCreateInput(0) : null}
      {root.childFolders
        .slice()
        .sort((a, b) => a.name.localeCompare(b.name))
        .map((folder) => renderFolder(folder, 0))}
      {root.childFiles
        .slice()
        .sort((a, b) => a.name.localeCompare(b.name))
        .map((file) => renderFile(file, 0))}
      {root.childFolders.length === 0 && root.childFiles.length === 0 && !creating ? (
        <p className="px-8 py-8 text-[12px] text-text-secondary">
          还没有文件，可拖入文件或点击上方按钮新建
        </p>
      ) : null}
    </div>
  );
}

function Modal_confirmDeleteFolder(name: string, onConfirm: () => void): void {
  // Deliberately lightweight: window.confirm avoids importing AntD's Modal.confirm
  // (already used elsewhere) purely for this one destructive action.
  if (window.confirm(`删除文件夹“${name}”及其中所有内容？此操作无法撤销。`)) {
    onConfirm();
  }
}

export default WorkspaceFileTree;
