import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import tailwindcss from '@tailwindcss/vite';
import { mvpMockApiPlugin } from './mocks/viteMockPlugin';
import { sseProxyPlugin } from './vite.sseProxyPlugin';

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
    };

  return {
    plugins: [
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
    build: {
      outDir: 'dist',
      sourcemap: false,
      minify: 'terser' as const,
      rollupOptions: { output: { inlineDynamicImports: true } },
      cssCodeSplit: false,
    },
  };
});
