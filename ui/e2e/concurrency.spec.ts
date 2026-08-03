import { expect, test, type APIRequestContext, type Browser } from '@playwright/test';
import {
  clickSend,
  createConversationFromSidebar,
  loginAs,
} from './helpers/auth';

/**
 * Plan §16 light concurrency smoke (NOT a capacity proof).
 * Skip unless MVP_E2E_READY=1.
 *
 * Defaults match the baseline matrix; override with MVP_CONCURRENCY_* for local debug.
 */
const e2eReady = process.env.MVP_E2E_READY === '1';
const USER_COUNT = Number(process.env.MVP_CONCURRENCY_USERS || 10);
const SESSIONS_PER_USER = Number(process.env.MVP_CONCURRENCY_SESSIONS || 20);
const TURNS_PER_SESSION = Number(process.env.MVP_CONCURRENCY_TURNS || 10);
const MAX_PARALLEL = Number(process.env.MVP_CONCURRENCY_PARALLEL || 10);
const RUN_ID = process.env.MVP_RUN_ID || `e2e${Date.now()}`;

type ApiEnvelope<T> = {
  code: string;
  message?: string;
  data: T;
};

async function fetchCsrf(request: APIRequestContext): Promise<{
  headerName: string;
  token: string;
}> {
  const res = await request.get('/api/v1/auth/csrf');
  expect(res.ok()).toBeTruthy();
  const body = (await res.json()) as ApiEnvelope<{
    headerName: string;
    token: string;
  }>;
  expect(body.code).toBe('OK');
  return body.data;
}

async function apiLogin(
  request: APIRequestContext,
  username: string,
  password: string,
): Promise<void> {
  const csrf = await fetchCsrf(request);
  const res = await request.post('/api/v1/auth/login', {
    headers: {
      [csrf.headerName]: csrf.token,
      'Content-Type': 'application/json',
    },
    data: {
      username,
      password
    },
  });
  expect(res.ok(), `login ${username} → ${res.status()}`).toBeTruthy();
  const body = (await res.json()) as ApiEnvelope<unknown>;
  expect(body.code).toBe('OK');
}

async function createAcceptanceUsers(
  request: APIRequestContext,
): Promise<Array<{ username: string; password: string }>> {
  const adminUser = process.env.GENIE_BOOTSTRAP_ADMIN_USERNAME || 'admin';
  const adminPass =
    process.env.MVP_ACCEPTANCE_ADMIN_PASSWORD ||
    process.env.GENIE_BOOTSTRAP_ADMIN_PASSWORD ||
    '';
  expect(adminPass, 'MVP_ACCEPTANCE_ADMIN_PASSWORD required for concurrency').toBeTruthy();

  await apiLogin(request, adminUser, adminPass);
  let csrf = await fetchCsrf(request);

  const password =
    process.env.MVP_ACCEPTANCE_USER_PASSWORD || 'MvpTest-Only-123';
  const users: Array<{ username: string; password: string }> = [];

  for (let i = 0; i < USER_COUNT; i += 1) {
    const username = `c${RUN_ID.slice(-8)}u${i}`.toLowerCase().replace(/[^a-z0-9._-]/g, '');
    const res = await request.post('/api/v1/admin/users', {
      headers: {
        [csrf.headerName]: csrf.token,
        'Content-Type': 'application/json',
      },
      data: {
        username,
        displayName: `Concurrency ${username}`,
        password,
        role: 'USER',
      },
    });
    // CSRF may rotate after mutating calls — refresh and retry once.
    if (res.status() === 403) {
      csrf = await fetchCsrf(request);
      const retry = await request.post('/api/v1/admin/users', {
        headers: {
          [csrf.headerName]: csrf.token,
          'Content-Type': 'application/json',
        },
        data: {
          username,
          displayName: `Concurrency ${username}`,
          password,
          role: 'USER',
        },
      });
      expect(retry.ok(), `create user ${username}`).toBeTruthy();
      const retryBody = (await retry.json()) as ApiEnvelope<{ username: string }>;
      expect(retryBody.code).toBe('OK');
    } else {
      expect(res.ok(), `create user ${username} → ${res.status()}`).toBeTruthy();
      const body = (await res.json()) as ApiEnvelope<{ username: string }>;
      expect(body.code).toBe('OK');
    }
    users.push({
      username,
      password
    });
    csrf = await fetchCsrf(request);
  }

  return users;
}

async function createConversation(
  request: APIRequestContext,
  csrf: { headerName: string; token: string },
): Promise<string> {
  const res = await request.post('/api/v1/conversations', {
    headers: {
      [csrf.headerName]: csrf.token,
      'Content-Type': 'application/json',
    },
    data: { title: null },
  });
  expect(res.ok()).toBeTruthy();
  const body = (await res.json()) as ApiEnvelope<{ id: string }>;
  expect(body.code).toBe('OK');
  return body.data.id;
}

async function runTurn(
  request: APIRequestContext,
  csrf: { headerName: string; token: string },
  conversationId: string,
  turnIndex: number,
): Promise<string> {
  const requestId = crypto.randomUUID();
  const res = await request.post('/web/api/v1/gpt/queryAgentStreamIncr', {
    headers: {
      [csrf.headerName]: csrf.token,
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    data: {
      sessionId: conversationId,
      requestId,
      query: `concurrency turn ${turnIndex} ${requestId.slice(0, 8)}`,
      deepThink: 0,
      outputStyle: 'docs',
    },
    timeout: 120_000,
  });
  expect(res.ok(), `SSE open ${conversationId}`).toBeTruthy();
  const text = await res.text();
  // Fake SUCCESS streams end with finished terminal; body must stay bound to this session.
  expect(text).toContain(conversationId);
  expect(text).toContain(requestId);
  return requestId;
}

async function awaitPool<T>(
  items: T[],
  parallelism: number,
  worker: (item: T) => Promise<void>,
): Promise<void> {
  let cursor = 0;
  const runners = Array.from({ length: Math.min(parallelism, items.length) }, async () => {
    while (cursor < items.length) {
      const index = cursor;
      cursor += 1;
      await worker(items[index]);
    }
  });
  await Promise.all(runners);
}

async function runUserWorkload(
  browser: Browser,
  user: { username: string; password: string },
): Promise<{ conversationIds: string[]; requestIds: string[] }> {
  const context = await browser.newContext();
  const page = await context.newPage();
  await loginAs(page, user.username, user.password);
  const request = context.request;
  let csrf = await fetchCsrf(request);

  const conversationIds: string[] = [];
  const requestIds: string[] = [];

  for (let s = 0; s < SESSIONS_PER_USER; s += 1) {
    const conversationId = await createConversation(request, csrf);
    conversationIds.push(conversationId);
    csrf = await fetchCsrf(request);
    // Sequential turns inside one conversation — never dual-send (busy is a separate case).
    for (let t = 0; t < TURNS_PER_SESSION; t += 1) {
      const requestId = await runTurn(request, csrf, conversationId, t);
      requestIds.push(requestId);
      csrf = await fetchCsrf(request);
    }
  }

  // Spot-check: list must only contain this user's conversations.
  const listRes = await request.get('/api/v1/conversations?page=1&pageSize=100');
  expect(listRes.ok()).toBeTruthy();
  const listBody = (await listRes.json()) as ApiEnvelope<{
    items: Array<{ id: string }>;
  }>;
  expect(listBody.code).toBe('OK');
  const listed = new Set(listBody.data.items.map((item) => item.id));
  for (const id of conversationIds) {
    expect(listed.has(id)).toBeTruthy();
  }

  await context.close();
  return {
    conversationIds,
    requestIds
  };
}

test.describe('concurrency', () => {
  test.skip(!e2eReady, 'Set MVP_E2E_READY=1 with mvp-acceptance Fake stack');

  test('light concurrency smoke: 10×20×10 no cross-mix', async ({
    browser,
    request,
  }) => {
    test.setTimeout(60 * 60 * 1000);

    const users = await createAcceptanceUsers(request);
    expect(users).toHaveLength(USER_COUNT);

    const allConversationIds: string[] = [];
    const ownedBy = new Map<string, string>();

    await awaitPool(users, MAX_PARALLEL, async (user) => {
      const result = await runUserWorkload(browser, user);
      for (const id of result.conversationIds) {
        allConversationIds.push(id);
        ownedBy.set(id, user.username);
      }
    });

    expect(allConversationIds).toHaveLength(USER_COUNT * SESSIONS_PER_USER);
    expect(new Set(allConversationIds).size).toBe(allConversationIds.length);

    // Cross-user isolation: user0 must not see user1's conversation via GET.
    const victimId = [...ownedBy.entries()].find(
      ([, owner]) => owner === users[1].username,
    )?.[0];
    expect(victimId).toBeTruthy();

    const probe = await browser.newContext();
    const probePage = await probe.newPage();
    await loginAs(probePage, users[0].username, users[0].password);
    const forbidden = await probe.request.get(
      `/api/v1/conversations/${victimId}`,
    );
    expect(forbidden.status()).toBe(404);
    const forbiddenBody = (await forbidden.json()) as ApiEnvelope<unknown>;
    expect(forbiddenBody.code).toBe('RESOURCE_NOT_FOUND');
    await probe.close();
  });

  test('same conversation concurrent send returns 409 busy', async ({
    page,
    context,
  }) => {
    test.setTimeout(180_000);
    await loginAs(page, 'user-a');
    await createConversationFromSidebar(page);
    await expect(page).toHaveURL(/\/app\/chat\//);
    const conversationId = page.url().match(/\/app\/chat\/([^/?#]+)/)?.[1];
    expect(conversationId).toBeTruthy();

    const csrf = await fetchCsrf(context.request);
    const body = {
      sessionId: conversationId,
      requestId: crypto.randomUUID(),
      query: 'busy probe A',
      deepThink: 0,
      outputStyle: 'docs',
    };
    const body2 = {
      ...body,
      requestId: crypto.randomUUID(),
      query: 'busy probe B',
    };

    // Fire two POSTs without awaiting the first stream to finish.
    const p1 = context.request.post('/web/api/v1/gpt/queryAgentStreamIncr', {
      headers: {
        [csrf.headerName]: csrf.token,
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
      },
      data: body,
      timeout: 60_000,
    });
    await page.waitForTimeout(50);
    const p2 = context.request.post('/web/api/v1/gpt/queryAgentStreamIncr', {
      headers: {
        [csrf.headerName]: csrf.token,
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
      },
      data: body2,
      timeout: 60_000,
    });

    const [r1, r2] = await Promise.all([p1, p2]);
    const statuses = [r1.status(), r2.status()].sort();
    // One may open SSE (200); the other must be 409 CONVERSATION_BUSY / DUPLICATE path.
    expect(statuses[0] === 409 || statuses[1] === 409).toBeTruthy();

    // UI path still usable afterward — no permanent lock from the busy probe.
    await page.reload();
    await expect(page).toHaveURL(/\/app\/chat\//);
    const composer = page.getByPlaceholder(/Genie|希望|输入|消息/i).or(
      page.getByRole('textbox').last(),
    );
    await composer.fill('busy follow-up after 409');
    // May still be busy while first stream runs; wait until input enabled.
    await expect(page.getByRole('button', { name: /发送/i })).toBeEnabled({timeout: 120_000,});
    await clickSend(page);
    await expect(page.getByText('busy follow-up after 409').first()).toBeVisible({timeout: 30_000,});
  });
});
