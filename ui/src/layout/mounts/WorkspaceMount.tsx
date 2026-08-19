import { useMemo, type ReactNode } from 'react';
import { useAuth } from '@/features/auth/useAuth';
import {
  WorkspacePanel,
  WorkspaceProvider,
} from '@/features/workspace';
import { createWorkspaceId } from '@/platform/workspace';

const STORAGE_PREFIX = 'joyagent.workspaceId.';

function readOrCreateWorkspaceId(userId: string): string {
  const key = `${STORAGE_PREFIX}${userId}`;
  try {
    const existing = localStorage.getItem(key)?.trim();
    if (existing) return existing;
    const next = createWorkspaceId();
    localStorage.setItem(key, next);
    return next;
  } catch {
    return createWorkspaceId();
  }
}

export interface WorkspaceMountProps {
  readonly conversationId?: string;
  readonly children?: ReactNode;
}

export default function WorkspaceMount({
  conversationId,
  children,
}: WorkspaceMountProps) {
  const { user } = useAuth();
  const workspaceId = useMemo(
    () => (user?.id ? readOrCreateWorkspaceId(user.id) : null),
    [user?.id],
  );

  if (!user?.id || !workspaceId) {
    return children ?? null;
  }

  return (
    <WorkspaceProvider
      userId={user.id}
      workspaceId={workspaceId}
      conversationId={conversationId}
    >
      {children ?? <WorkspacePanel />}
    </WorkspaceProvider>
  );
}
