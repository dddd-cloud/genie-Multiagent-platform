import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '../../../../mocks/server';
import { resetMockState } from '../../../../mocks/handlers';
import { setCsrf, clearCsrf } from '@/features/auth/csrf';
import { phase2Get } from '../client';
import { MvpApiError } from '@/services';

describe('phase2Get retry', () => {
  beforeAll(() => {
    server.listen({ onUnhandledRequest: 'error' });
  });

  afterEach(() => {
    server.resetHandlers();
    resetMockState();
    clearCsrf();
  });

  afterAll(() => {
    server.close();
  });

  beforeEach(() => {
    resetMockState();
    setCsrf({
      headerName: 'X-XSRF-TOKEN',
      token: 'mvp-mock-csrf-token',
    });
  });

  it('retries GET once on HTTP 5xx then succeeds', async () => {
    let hits = 0;
    server.use(
      http.get('/api/v2/retry-probe', () => {
        hits += 1;
        if (hits === 1) {
          return HttpResponse.json(
            {
              code: 'INTERNAL_ERROR',
              message: 'temporary',
              data: null,
            },
            { status: 500 },
          );
        }
        return HttpResponse.json({
          code: 'OK',
          message: 'success',
          data: { ok: true },
        });
      }),
    );

    const data = await phase2Get<{ ok: boolean }>('/api/v2/retry-probe');
    expect(hits).toBe(2);
    expect(data).toEqual({ ok: true });
  });

  it('does not retry GET on HTTP 404', async () => {
    let hits = 0;
    server.use(
      http.get('/api/v2/retry-probe-404', () => {
        hits += 1;
        return HttpResponse.json(
          {
            code: 'RESOURCE_NOT_FOUND',
            message: 'missing',
            data: null,
          },
          { status: 404 },
        );
      }),
    );

    await expect(phase2Get('/api/v2/retry-probe-404')).rejects.toBeInstanceOf(
      MvpApiError,
    );
    expect(hits).toBe(1);
  });

  it('does not retry GET on business error with HTTP 200', async () => {
    let hits = 0;
    server.use(
      http.get('/api/v2/retry-probe-biz', () => {
        hits += 1;
        return HttpResponse.json({
          code: 'VALIDATION_ERROR',
          message: 'bad',
          data: null,
        });
      }),
    );

    await expect(phase2Get('/api/v2/retry-probe-biz')).rejects.toMatchObject({
      code: 'VALIDATION_ERROR',
      httpStatus: 200,
    });
    expect(hits).toBe(1);
  });
});
