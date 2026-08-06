import type { ServerResponse } from 'node:http';
import http from 'node:http';
import https from 'node:https';
import type { Connect, Plugin } from 'vite';

/**
 * Vite's default http-proxy buffers chunked SSE responses, so the browser only
 * receives orchestration_trace / orchestration events after the stream ends.
 * This middleware pipes Accept: text/event-stream requests and flushes each chunk.
 */
export function sseProxyPlugin(targetBaseUrl: string | undefined): Plugin {
  const middleware = createSseProxyMiddleware(targetBaseUrl);
  return {
    name: 'sse-stream-proxy',
    configureServer(server) {
      if (middleware) {
        server.middlewares.use(middleware);
      }
    },
    configurePreviewServer(server) {
      if (middleware) {
        server.middlewares.use(middleware);
      }
    },
  };
}

function createSseProxyMiddleware(
  targetBaseUrl: string | undefined,
): Connect.NextHandleFunction | null {
  if (!targetBaseUrl) {
    return null;
  }
  let target: URL;
  try {
    target = new URL(targetBaseUrl);
  } catch {
    return null;
  }
  const transport = target.protocol === 'https:' ? https : http;

  return (req, res, next) => {
    const accept = String(req.headers.accept || '');
    const url = req.url || '';
    if (
      !accept.includes('text/event-stream') ||
      !(url.startsWith('/web/') || url.startsWith('/api/'))
    ) {
      next();
      return;
    }

    const upstreamUrl = new URL(url, target);
    const headers: Record<string, string | string[] | undefined> = {
      ...req.headers,
      host: target.host,
      accept: 'text/event-stream',
      connection: 'keep-alive',
      'cache-control': 'no-cache',
    };

    const upstream = transport.request(
      {
        protocol: target.protocol,
        hostname: target.hostname,
        port: target.port || (target.protocol === 'https:' ? 443 : 80),
        method: req.method,
        path: `${upstreamUrl.pathname}${upstreamUrl.search}`,
        headers,
      },
      (upstreamRes) => {
        res.statusCode = upstreamRes.statusCode || 502;
        for (const [key, value] of Object.entries(upstreamRes.headers)) {
          if (value !== undefined) {
            res.setHeader(key, value);
          }
        }
        res.setHeader('Cache-Control', 'no-cache, no-transform');
        res.setHeader('X-Accel-Buffering', 'no');
        res.setHeader('Connection', 'keep-alive');
        if (typeof (res as ServerResponse & { flushHeaders?: () => void }).flushHeaders === 'function') {
          (res as ServerResponse & { flushHeaders: () => void }).flushHeaders();
        }

        upstreamRes.on('data', (chunk: Buffer) => {
          res.write(chunk);
          const flushable = res as ServerResponse & { flush?: () => void };
          if (typeof flushable.flush === 'function') {
            flushable.flush();
          }
        });
        upstreamRes.on('end', () => res.end());
        upstreamRes.on('error', () => {
          if (!res.writableEnded) {
            res.end();
          }
        });
      },
    );

    upstream.on('error', () => {
      if (!res.headersSent) {
        res.statusCode = 502;
      }
      if (!res.writableEnded) {
        res.end();
      }
    });

    req.on('aborted', () => upstream.destroy());
    res.on('close', () => upstream.destroy());
    req.pipe(upstream);
  };
}
