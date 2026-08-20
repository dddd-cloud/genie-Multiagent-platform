import { applyCsrfHeaders } from '@/features/auth/csrf';
import { MvpApiError } from '@/services/apiError';
import { notifyMvpError } from '@/features/auth/mvpErrorBus';

export interface ConversationAttachmentResponse {
  id: string;
  fileName: string;
  fileType: string;
  sizeBytes: number;
  extractedChars: number;
  truncated: boolean;
}

type ApiEnvelope<T> = {
  code?: unknown;
  message?: unknown;
  data?: T;
};

function asError(status: number, body: unknown, fallback: string): MvpApiError {
  if (body && typeof body === 'object' && 'code' in body) {
    const envelope = body as ApiEnvelope<unknown>;
    const code = typeof envelope.code === 'string' ? envelope.code : 'INTERNAL_ERROR';
    const message =
      typeof envelope.message === 'string' && envelope.message.trim()
        ? envelope.message
        : fallback;
    return new MvpApiError(status, code, message, envelope.data ?? null);
  }
  return new MvpApiError(status, 'INTERNAL_ERROR', fallback, null);
}

export function uploadConversationAttachment(
  conversationId: string,
  file: File,
  onProgress: (percent: number) => void,
  signal: AbortSignal,
): Promise<ConversationAttachmentResponse> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    const url = `/api/v1/conversations/${encodeURIComponent(conversationId)}/attachments`;
    xhr.open('POST', url);
    xhr.withCredentials = true;
    const headers = applyCsrfHeaders();
    for (const [key, value] of Object.entries(headers)) {
      xhr.setRequestHeader(key, value);
    }
    xhr.upload.onprogress = (event) => {
      if (!event.lengthComputable || event.total <= 0) return;
      const percent = Math.min(90, Math.round((event.loaded / event.total) * 90));
      onProgress(percent);
    };
    xhr.onerror = () => {
      reject(new MvpApiError(0, 'INTERNAL_ERROR', '上传失败', null));
    };
    xhr.onabort = () => {
      reject(new DOMException('The operation was aborted.', 'AbortError'));
    };
    xhr.onload = () => {
      let parsed: unknown = null;
      try {
        parsed = xhr.responseText ? JSON.parse(xhr.responseText) : null;
      } catch {
        parsed = null;
      }
      if (xhr.status < 200 || xhr.status >= 300) {
        const error = asError(xhr.status, parsed, '上传失败');
        notifyMvpError(error);
        reject(error);
        return;
      }
      const envelope = parsed as ApiEnvelope<ConversationAttachmentResponse>;
      if (!envelope || envelope.code !== 'OK' || !envelope.data?.id) {
        const error = asError(xhr.status, parsed, '上传失败');
        notifyMvpError(error);
        reject(error);
        return;
      }
      onProgress(100);
      resolve(envelope.data);
    };
    signal.addEventListener('abort', () => xhr.abort(), { once: true });
    const form = new FormData();
    form.append('file', file);
    xhr.send(form);
  });
}

export async function deleteConversationAttachment(
  conversationId: string,
  attachmentId: string,
): Promise<void> {
  const headers = applyCsrfHeaders({
    'Content-Type': 'application/json',
  });
  const response = await fetch(
    `/api/v1/conversations/${encodeURIComponent(conversationId)}/attachments/${encodeURIComponent(attachmentId)}`,
    {
      method: 'DELETE',
      credentials: 'include',
      headers,
    },
  );
  if (response.status === 404) {
    return;
  }
  if (!response.ok) {
    let parsed: unknown = null;
    try {
      parsed = await response.json();
    } catch {
      parsed = null;
    }
    throw asError(response.status, parsed, '取消上传失败');
  }
}
