import { createReadStream, cpSync, existsSync, statSync } from 'node:fs';
import { defineConfig, loadEnv, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import tailwindcss from '@tailwindcss/vite';
import { mvpMockApiPlugin } from './mocks/viteMockPlugin';
import { sseProxyPlugin } from './vite.sseProxyPlugin';

const PYODIDE_PUBLIC_PATH = '/pyodide/';

function pyodideAssetsPlugin(): Plugin {
  const sourceDir = path.resolve(__dirname, 'node_modules', 'pyodide');
  const safeAssetName = /^[A-Za-z0-9][A-Za-z0-9._+-]*$/;
  return {
    name: 'local-pyodide-assets',
    configureServer(server) {
      server.middlewares.use(PYODIDE_PUBLIC_PATH, (req, res, next) => {
        const assetName = decodeURIComponent((req.url ?? '').split('?')[0])
          .replace(/^\/+/, '');
        if (!safeAssetName.test(assetName)) {
          next();
          return;
        }
        const filePath = path.resolve(sourceDir, assetName);
        if (
          !filePath.startsWith(`${sourceDir}${path.sep}`) ||
          !existsSync(filePath) ||
          !statSync(filePath).isFile()
        ) {
          next();
          return;
        }
        const extension = path.extname(assetName).toLowerCase();
        const contentTypes: Record<string, string> = {
          '.js': 'text/javascript; charset=utf-8',
          '.mjs': 'text/javascript; charset=utf-8',
          '.json': 'application/json; charset=utf-8',
          '.wasm': 'application/wasm',
          '.zip': 'application/zip',
          '.whl': 'application/zip',
        };
        res.setHeader('Content-Type', contentTypes[extension] ?? 'application/octet-stream');
        res.setHeader('Cache-Control', 'public, max-age=31536000, immutable');
        createReadStream(filePath).pipe(res);
      });
    },
    closeBundle() {
      if (existsSync(sourceDir)) {
        cpSync(sourceDir, path.resolve(__dirname, 'dist', 'pyodide'), {
          recursive: true,
        });
      }
    },
  };
}

export default defineConfig(({ command, mode, isPreview }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const isMvpMock = mode === 'mvp-mock';

  if (
    (command === 'serve' || isPreview) &&
    !isMvpMock &&
    !env.SERVICE_BASE_URL
  ) {
    throw new Error(
      'SERVICE_BASE_URL is required for vite serve/preview (proxy target). Set it in ui/.env or the environment.',
    );
  }

  // mvp-mock serves APIs via mvpMockApiPlugin — do not proxy to a dead backend.
  // SSE streams use sseProxyPlugin; Vite http-proxy buffers text/event-stream.
  const proxy = isMvpMock
    ? undefined
    : {
      '/api': {
        target: env.SERVICE_BASE_URL,
        changeOrigin: true,
      },
      '/web': {
        target: env.SERVICE_BASE_URL,
        changeOrigin: true,
      },
      '/data': {
        target: env.SERVICE_BASE_URL,
        changeOrigin: true,
      },
      '/v1/file_tool': {
        target:
          env.FILE_TOOL_BASE_URL ||
          process.env.FILE_TOOL_BASE_URL ||
          'http://genie-tool:1601',
        changeOrigin: true,
      },
    };

  return {
    plugins: [
      pyodideAssetsPlugin(),
      react(),
      tailwindcss(),
      mvpMockApiPlugin(isMvpMock),
      sseProxyPlugin(isMvpMock ? undefined : env.SERVICE_BASE_URL),
    ],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src'),
        crypto: 'crypto-browserify',
      },
    },
    css: {preprocessorOptions: {less: { javascriptEnabled: true },},},
    server: {
      host: '0.0.0.0',
      port: 3000,
      allowedHosts: true,
      proxy,
      fs: {
        // Allow reading docs/mvp-contract at repo root (contract fixtures / MSW later).
        allow: [path.resolve(__dirname, '..')],
      },
    },
    preview: {proxy,},
    worker: {
      format: 'es',
    },
    build: {
      outDir: 'dist',
      sourcemap: false,
      minify: 'terser' as const,
      // Workers (Pyodide) require separate chunks — do not inlineDynamicImports.
      cssCodeSplit: false,
    },
  };
});
