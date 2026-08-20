import { useCallback, useRef, useState } from 'react';
import { message } from 'antd';
import {
  COMPOSER_ATTACHMENT_MAX_BYTES,
  COMPOSER_ATTACHMENT_MAX_FILES,
  composerAttachmentTypeOf,
} from './constants';
import {
  deleteConversationAttachment,
  uploadConversationAttachment,
} from '@/services/conversation/attachments';

export type ComposerAttachmentStatus = 'uploading' | 'ready' | 'error';

export interface ComposerAttachment {
  readonly clientId: string;
  readonly attachmentId?: string;
  readonly name: string;
  readonly type: string;
  readonly size: number;
  readonly status: ComposerAttachmentStatus;
  readonly progress: number;
  readonly errorMessage?: string;
}

export interface UseComposerAttachmentsOptions {
  conversationId?: string;
  ensureConversation?: () => Promise<string | null>;
}

function isAbortError(error: unknown): boolean {
  return (
    (error instanceof DOMException && error.name === 'AbortError') ||
    (error instanceof Error && error.name === 'AbortError')
  );
}

export function useComposerAttachments(options: UseComposerAttachmentsOptions) {
  const [attachments, setAttachments] = useState<ComposerAttachment[]>([]);
  const attachmentsRef = useRef<ComposerAttachment[]>([]);
  const controllersRef = useRef(new Map<string, AbortController>());
  const conversationIdRef = useRef(options.conversationId);
  conversationIdRef.current = options.conversationId;
  const ensureConversationRef = useRef(options.ensureConversation);
  ensureConversationRef.current = options.ensureConversation;
  attachmentsRef.current = attachments;

  const patch = useCallback(
    (clientId: string, patchValue: Partial<ComposerAttachment>) => {
      setAttachments((prev) =>
        prev.map((item) => (item.clientId === clientId ? { ...item, ...patchValue } : item)),
      );
    },
    [],
  );

  const addFiles = useCallback(
    async (fileList: FileList | File[]) => {
      const incoming = Array.from(fileList);
      if (incoming.length === 0) return;

      const remaining = COMPOSER_ATTACHMENT_MAX_FILES - attachmentsRef.current.length;
      if (remaining <= 0) {
        message.warning(`最多上传 ${COMPOSER_ATTACHMENT_MAX_FILES} 个文件`);
        return;
      }

      const accepted: File[] = [];
      for (const file of incoming) {
        if (accepted.length >= remaining) {
          message.warning(`最多上传 ${COMPOSER_ATTACHMENT_MAX_FILES} 个文件`);
          break;
        }
        const type = composerAttachmentTypeOf(file.name);
        if (!type) {
          message.warning(`不支持的文件类型：${file.name}`);
          continue;
        }
        if (file.size <= 0 || file.size > COMPOSER_ATTACHMENT_MAX_BYTES) {
          message.warning(`${file.name} 超过 10MB 或为空`);
          continue;
        }
        accepted.push(file);
      }
      if (accepted.length === 0) return;

      const created: ComposerAttachment[] = accepted.map((file) => ({
        clientId: crypto.randomUUID(),
        name: file.name,
        type: composerAttachmentTypeOf(file.name) ?? '',
        size: file.size,
        status: 'uploading',
        progress: 8,
      }));
      setAttachments((prev) => [...prev, ...created]);

      let conversationId = conversationIdRef.current ?? '';
      if (!conversationId) {
        try {
          conversationId = (await ensureConversationRef.current?.()) ?? '';
        } catch {
          conversationId = '';
        }
      }
      if (!conversationId) {
        created.forEach((item) => {
          patch(item.clientId, {
            status: 'error',
            progress: 0,
            errorMessage: '创建会话失败',
          });
        });
        return;
      }
      conversationIdRef.current = conversationId;

      created.forEach((item, index) => {
        const file = accepted[index];
        const controller = new AbortController();
        controllersRef.current.set(item.clientId, controller);
        void uploadConversationAttachment(
          conversationId,
          file,
          (percent) => patch(item.clientId, { progress: percent }),
          controller.signal,
        )
          .then((uploaded) => {
            controllersRef.current.delete(item.clientId);
            patch(item.clientId, {
              status: 'ready',
              progress: 100,
              attachmentId: uploaded.id,
              type: uploaded.fileType || item.type,
            });
          })
          .catch((error) => {
            controllersRef.current.delete(item.clientId);
            if (isAbortError(error)) {
              return;
            }
            patch(item.clientId, {
              status: 'error',
              progress: 0,
              errorMessage: error instanceof Error ? error.message : '上传失败',
            });
          });
      });
    },
    [patch],
  );

  const removeAttachment = useCallback((clientId: string) => {
    const snapshot = attachmentsRef.current.find((item) => item.clientId === clientId);
    setAttachments((prev) => prev.filter((item) => item.clientId !== clientId));
    const controller = controllersRef.current.get(clientId);
    controller?.abort();
    controllersRef.current.delete(clientId);
    const conversationId = conversationIdRef.current;
    if (snapshot?.attachmentId && conversationId) {
      void deleteConversationAttachment(conversationId, snapshot.attachmentId).catch(() => undefined);
    }
  }, []);

  const clearAttachments = useCallback(() => {
    controllersRef.current.forEach((controller) => controller.abort());
    controllersRef.current.clear();
    setAttachments([]);
  }, []);

  const uploading = attachments.some((item) => item.status === 'uploading');
  const ready = attachments.filter((item) => item.status === 'ready' && item.attachmentId);
  const readyIds = ready
    .map((item) => item.attachmentId)
    .filter((id): id is string => Boolean(id));

  return {
    attachments,
    addFiles,
    removeAttachment,
    clearAttachments,
    uploading,
    ready,
    readyIds,
  };
}
