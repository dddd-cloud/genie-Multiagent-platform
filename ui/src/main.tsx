// import React, { StrictMode } from 'react'; // 暂时移除严格模式
import { createRoot } from 'react-dom/client';
import App from './App';
import './global.css';

async function prepare(): Promise<void> {
  if (import.meta.env.MODE === 'mvp-mock') {
    try {
      const { worker } = await import('../mocks/browser');
      await worker.start({
        serviceWorker: { url: '/mockServiceWorker.js' },
        onUnhandledRequest: 'bypass',
        quiet: true,
      });
    } catch (error) {
      // Vite mvp-mock middleware still serves /api|/web|/data (see viteMockPlugin).
      console.warn(
        '[mvp-mock] MSW service worker unavailable; using Vite mock API',
        error,
      );
    }
  }
}

const root = document.getElementById('root');

prepare()
  .then(() => {
    if (root) {
      createRoot(root).render(<App />);
    } else {
      console.error('Root element not found');
    }
  })
  .catch((error) => {
    console.error('Failed to bootstrap app', error);
  });
