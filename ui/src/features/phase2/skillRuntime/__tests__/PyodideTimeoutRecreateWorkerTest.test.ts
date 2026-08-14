import { describe, expect, it, afterEach } from 'vitest';
import {
  PyodideRuntimeManager,
  resetSharedPyodideRuntimeManager,
} from '../PyodideRuntimeManager';

class SilentAfterReadyWorker extends EventTarget {
  onmessage: ((ev: MessageEvent) => void) | null = null;

  postMessage(data: { type: string }) {
    if (data.type === 'init') {
      queueMicrotask(() => {
        this.dispatchEvent(
          new MessageEvent('message', { data: { type: 'ready' } }),
        );
      });
    }
    // execute: intentionally never responds
  }

  terminate() {}

  addEventListener(
    type: string,
    listener: EventListenerOrEventListenerObject,
    options?: boolean | AddEventListenerOptions,
  ) {
    super.addEventListener(type, listener, options);
  }

  removeEventListener(
    type: string,
    listener: EventListenerOrEventListenerObject,
    options?: boolean | EventListenerOptions,
  ) {
    super.removeEventListener(type, listener, options);
  }
}

describe('PyodideTimeoutRecreateWorkerTest', () => {
  afterEach(() => {
    resetSharedPyodideRuntimeManager();
  });

  it('returns SKILL_EXECUTION_TIMEOUT and leaves state FAILED', async () => {
    const manager = new PyodideRuntimeManager({
      indexURL: 'https://cdn.example/pyodide/',
      createWorker: () => new SilentAfterReadyWorker() as unknown as Worker,
      defaultTimeoutMs: 30,
    });

    await manager.ensureReady();
    const msg = await manager.execute(
      'exec-timeout',
      'main',
      new ArrayBuffer(4),
      40,
    );

    expect(msg).toMatchObject({
      type: 'result',
      executionId: 'exec-timeout',
      success: false,
      errorCode: 'SKILL_EXECUTION_TIMEOUT',
    });
    expect(manager.getState()).toBe('FAILED');
    manager.dispose();
  });
});
