import type { IncomingMessage, ServerResponse } from 'node:http';
import type { Plugin, ViteDevServer } from 'vite';
import type { RequestHandler } from 'msw';

function readBody(req: IncomingMessage): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    req.on('data', (chunk: Buffer) => {
      chunks.push(chunk);
    });
    req.on('end', () => {
      resolve(Buffer.concat(chunks));
    });
    req.on('error', reject);
  });
}

function shouldMock(pathname: string): boolean {
  return (
    pathname.startsWith('/api/') ||
    pathname.startsWith('/web/') ||
    pathname.startsWith('/data/')
  );
}

type MockModules = {
  getResponse: (
    handlers: RequestHandler[],
    request: Request,
    resolutionContext?: { baseUrl?: string },
  ) => Promise<Response | undefined>;
  handlers: RequestHandler[];
};

async function loadMockModules(server: ViteDevServer): Promise<MockModules> {
  // Load via Vite SSR pipeline so `*.ndjson?raw` imports resolve.
  const msw = (await server.ssrLoadModule('msw')) as {
    getResponse: MockModules['getResponse'];
  };
  const handlersMod = (await server.ssrLoadModule('/mocks/handlers.ts')) as {
    handlers: RequestHandler[];
  };
  return {
    getResponse: msw.getResponse,
    handlers: handlersMod.handlers,
  };
}

async function writeMockResponse(
  response: Response,
  res: ServerResponse,
): Promise<void> {
  res.statusCode = response.status;
  response.headers.forEach((value, key) => {
    if (key.toLowerCase() === 'content-length') return;
    res.setHeader(key, value);
  });

  if (response.body) {
    const buf = Buffer.from(await response.arrayBuffer());
    res.end(buf);
    return;
  }
  res.end();
}

/**
 * Serve MSW handlers from Vite itself in mvp-mock mode.
 * Browser Service Worker is flaky in some embedded browsers; without this
 * fallback, /api/* falls through to the dead backend proxy and login fails.
 */
export function mvpMockApiPlugin(enabled: boolean): Plugin {
  return {
    name: 'mvp-mock-api',
    configureServer(server) {
      if (!enabled) {
        return;
      }

      let modulesPromise: Promise<MockModules> | null = null;
      const getModules = () => {
        if (!modulesPromise) {
          modulesPromise = loadMockModules(server).catch((error) => {
            modulesPromise = null;
            throw error;
          });
        }
        return modulesPromise;
      };

      server.middlewares.use(async (req, res, next) => {
        try {
          if (!req.url || !req.method) {
            next();
            return;
          }

          const host = req.headers.host || 'localhost:3000';
          const url = new URL(req.url, `http://${host}`);
          if (!shouldMock(url.pathname)) {
            next();
            return;
          }

          const { getResponse, handlers } = await getModules();
          const method = req.method.toUpperCase();
          const rawBody =
            method === 'GET' || method === 'HEAD'
              ? undefined
              : await readBody(req);

          const headers = new Headers();
          for (const [key, value] of Object.entries(req.headers)) {
            if (value == null) continue;
            if (Array.isArray(value)) {
              for (const item of value) headers.append(key, item);
            } else {
              headers.set(key, value);
            }
          }

          const init: RequestInit = {
            method,
            headers,
          };
          if (rawBody && rawBody.length > 0) {
            init.body = new Uint8Array(rawBody);
          }

          const request = new Request(url, init);
          // Relative handler paths need baseUrl (same as @mswjs/http-middleware).
          const response = await getResponse(handlers, request, {baseUrl: url.origin,});

          if (!response) {
            const body = JSON.stringify({
              code: 'NOT_FOUND',
              message: `No mock handler for ${method} ${url.pathname}`,
              data: null,
            });
            res.statusCode = 404;
            res.setHeader('Content-Type', 'application/json');
            res.end(body);
            return;
          }

          await writeMockResponse(response, res);
        } catch (error) {
          next(error);
        }
      });
    },
  };
}
