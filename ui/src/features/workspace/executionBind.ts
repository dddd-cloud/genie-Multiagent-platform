import type { WorkspaceScope } from '@/platform/workspace/types';
import type { WorkspaceService } from '@/services/workspace/workspaceService';

export interface WorkspaceExecutionBind {
  readonly service: WorkspaceService;
  readonly scope: WorkspaceScope;
  readonly fileIds: readonly string[];
}

let bound: WorkspaceExecutionBind | null = null;

export function bindWorkspaceExecutionContext(
  next: WorkspaceExecutionBind | null,
): void {
  bound = next;
}

export function getBoundWorkspaceExecutionContext(): WorkspaceExecutionBind | null {
  return bound;
}
