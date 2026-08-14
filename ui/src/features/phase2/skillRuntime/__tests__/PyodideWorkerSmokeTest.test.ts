import { describe, expect, it, afterEach } from 'vitest';
import {
  PyodideRuntimeManager,
  resetSharedPyodideRuntimeManager,
} from '../PyodideRuntimeManager';

class FakeWorker extends EventTarget {
  onmessage: ((ev: MessageEvent) => void) | null = null;

  postMessage(data: { type: string; executionId?: string }) {
    if (data.type === 'init') {
      queueMicrotask(() => {
        this.dispatchEvent(
          new MessageEvent('message', { data: { type: 'ready' } }),
        );
      });
    }
    if (data.type === 'execute') {
      queueMicrotask(() => {
        this.dispatchEvent(
          new MessageEvent('message', {
            data: {
              type: 'result',
              executionId: data.executionId,
              success: true,
              outputJson: '{}',
              stdout: null,
              stderr: null,
              errorCode: null,
              message: null,
            },
          }),
        );
      });
    }
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

describe('PyodideWorkerSmokeTest', () => {
  afterEach(() => {
    resetSharedPyodideRuntimeManager();
  });

  it('initializes via fake Worker ready then returns execute result', async () => {
    const manager = new PyodideRuntimeManager({
      indexURL: 'https://cdn.example/pyodide/',
      createWorker: () => new FakeWorker() as unknown as Worker,
      defaultTimeoutMs: 5_000,
    });

    await manager.ensureReady();
    expect(manager.getState()).toBe('READY');

    const msg = await manager.execute(
      'exec-1',
      'main',
      new ArrayBuffer(4),
      5_000,
    );
    expect(msg).toMatchObject({
      type: 'result',
      executionId: 'exec-1',
      success: true,
      outputJson: '{}',
    });
    expect(manager.getState()).toBe('READY');
    manager.dispose();
  });
});
