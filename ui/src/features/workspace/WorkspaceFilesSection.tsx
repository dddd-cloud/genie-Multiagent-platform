import { useState } from 'react';
import { DownOutlined, RightOutlined } from '@ant-design/icons';
import { message } from 'antd';
import type { UserWorkspace } from '@/platform/workspace/catalog';
import type { WorkspaceFile } from '@/platform/workspace/types';
import { useWorkspaceFiles } from './useWorkspaceFiles';
import { WorkspaceFileTree } from './WorkspaceFileTree';
import { WorkspaceFilePreviewModal } from './WorkspaceFilePreviewModal';
import { runWorkspacePython } from './workspacePythonRuntime';

export interface WorkspaceFilesSectionProps {
  readonly userId: string;
  readonly workspace: UserWorkspace;
  readonly defaultExpanded: boolean;
  readonly isActive: boolean;
}

export function WorkspaceFilesSection({
  userId,
  workspace,
  defaultExpanded,
  isActive,
}: WorkspaceFilesSectionProps) {
  const [expanded, setExpanded] = useState(defaultExpanded);
  const workspaceState = useWorkspaceFiles(userId, workspace.id);
  const [previewFile, setPreviewFile] = useState<WorkspaceFile | null>(null);
  const [running, setRunning] = useState(false);

  const runPython = async (file: WorkspaceFile) => {
    setRunning(true);
    try {
      const inputs = (
        await Promise.all(
          workspaceState.files.map(async (item) => {
            const bytes = await workspaceState.readFile(item.id);
            return bytes ? [{ name: item.name, mimeType: item.mimeType, bytes }] : [];
          }),
        )
      ).flat();
      const result = await runWorkspacePython(file.name, inputs);
      await workspaceState.saveRuntimeFiles(result.files);
      const output = [result.stdout, result.stderr, result.error].filter(Boolean).join('\n');
      if (result.success) {
        message.success(output ? `运行完成：${output.slice(0, 200)}` : '运行完成');
      } else {
        message.error(output || '运行失败');
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '浏览器 Python 执行失败');
    } finally {
      setRunning(false);
    }
  };

  return (
    <div className="border-b border-border last:border-b-0">
      <button
        type="button"
        className="flex w-full items-center gap-6 px-8 py-8 text-[13px] font-medium text-text-primary hover:bg-[#F5F5F7]"
        onClick={() => setExpanded((value) => !value)}
      >
        {expanded ? <DownOutlined className="text-[10px] text-text-secondary" /> : <RightOutlined className="text-[10px] text-text-secondary" />}
        <span className="min-w-0 flex-1 truncate text-left">{workspace.name}</span>
        {isActive ? <span className="rounded-[4px] bg-[#EFF3FF] px-6 py-1 text-[10px] text-[#3562FA]">当前</span> : null}
      </button>
      {expanded ? (
        <div className="px-4 pb-8">
          {workspaceState.status === 'loading' ? (
            <p className="px-8 py-8 text-[12px] text-text-secondary">正在加载…</p>
          ) : workspaceState.status === 'unavailable' ? (
            <p className="px-8 py-8 text-[12px] text-text-secondary">当前浏览器不支持持久化工作区</p>
          ) : (
            <WorkspaceFileTree
              workspaceState={workspaceState}
              onOpenFile={setPreviewFile}
              selectedFileId={previewFile?.id}
            />
          )}
        </div>
      ) : null}
      <WorkspaceFilePreviewModal
        file={previewFile}
        onClose={() => setPreviewFile(null)}
        readFile={workspaceState.readFile}
        onRunPython={runPython}
        running={running}
      />
    </div>
  );
}

export default WorkspaceFilesSection;
