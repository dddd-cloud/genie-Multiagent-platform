import { setupWorker } from 'msw/browser';
import { handlers } from './handlers';

/**
 * Browser MSW worker — only import when `import.meta.env.MODE === 'mvp-mock'`.
 */
export const worker = setupWorker(...handlers);
