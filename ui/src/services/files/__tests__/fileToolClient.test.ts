import { afterEach, describe, expect, it, vi } from 'vitest';
import { buildWorkspaceScope } from '@/platform/workspace/scope';
import { FileToolWorkspaceAdapter } from '@/services/files/fileToolClient';

const scopeA = buildWorkspaceScope('user-a', 'workspace-a', 'conversation-a');
const noConversation = buildWorkspaceScope('user-a', 'workspace-a');

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('FileToolWorkspaceAdapter', () => {
  it('lists through the authenticated Java workspace proxy and injects CSRF', async () => {
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      expect(String(url)).toContain('/api/v2/workspaces/conversation-a/files');
      expect(String(url)).not.toContain('clientRequestId');
      const headers = init?.headers as Record<string, string>;
      expect(headers['X-XSRF-TOKEN'] ?? headers['x-xsrf-token']).toBeUndefined();
      return jsonResponse({
        code: 'OK',
        data: {
          results: [{ requestId: 'workspace-v1-abc', fileName: 'report.csv', fileSize: 5 }],
        },
      });
    });
    vi.stubGlobal('fetch', fetchMock);
    const adapter = new FileToolWorkspaceAdapter();

    const files = await adapter.list(scopeA);

    expect(files).toEqual([
      expect.objectContaining({
        fileName: 'report.csv',
        downloadUrl: '/api/v2/workspaces/conversation-a/files/report.csv/download',
        domainUrl: '/api/v2/workspaces/conversation-a/files/report.csv/preview',
      }),
    ]);
  });

  it('returns an empty remote list when the page has no conversation', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const adapter = new FileToolWorkspaceAdapter();

    await expect(adapter.list(noConversation)).resolves.toEqual([]);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('rejects an upload response without the matching file name', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ code: 'OK', data: { fileName: 'renamed.csv', fileSize: 4 } })),
    );
    const adapter = new FileToolWorkspaceAdapter();

    await expect(
      adapter.upload(scopeA, new Blob(['test'], { type: 'text/csv' }), 'report.csv'),
    ).rejects.toMatchObject({ message: '文件服务未返回文件信息' });
  });

  it('does not trust a same-origin path outside the file route', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse({
          code: 'OK',
          data: {
            results: [
              {
                requestId: 'workspace-v1-abc',
                fileName: 'report.csv',
                fileSize: 4,
                downloadUrl: '/v1/file_tool_evil/download/foreign/report.csv',
                domainUrl: 'https://example.invalid/v1/file_tool/preview/foreign/report.csv',
              },
            ],
          },
        }),
      ),
    );
    const adapter = new FileToolWorkspaceAdapter();

    const files = await adapter.list(scopeA);

    expect(files).toEqual([
      expect.objectContaining({
        downloadUrl: '/api/v2/workspaces/conversation-a/files/report.csv/download',
        domainUrl: '/api/v2/workspaces/conversation-a/files/report.csv/preview',
      }),
    ]);
  });
});
