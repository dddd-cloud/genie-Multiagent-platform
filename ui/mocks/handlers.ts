import { http, HttpResponse, type HttpHandler } from 'msw';
import type { ApiResponse, ConversationListItem, ConversationResponse, UserResponse } from '../src/contracts';
import authSuccess from '../../docs/mvp-contract/fixtures/msw/auth-success.json';
import auth401 from '../../docs/mvp-contract/fixtures/msw/auth-401.json';
import emptyConversations from '../../docs/mvp-contract/fixtures/msw/empty-conversations.json';
import conversationWithReactHistory from '../../docs/mvp-contract/fixtures/msw/conversation-with-react-history.json';
import conversationWithPlanHistory from '../../docs/mvp-contract/fixtures/msw/conversation-with-plan-history.json';
import conversationFailed from '../../docs/mvp-contract/fixtures/msw/conversation-failed.json';
import conversationInterrupted from '../../docs/mvp-contract/fixtures/msw/conversation-interrupted.json';
import conversationBusy from '../../docs/mvp-contract/fixtures/msw/conversation-busy.json';
import userIsolation404 from '../../docs/mvp-contract/fixtures/msw/user-isolation-404.json';
import { createFakeSseResponse, type FakeSseScenario } from './fakeSse';

const CSRF_HEADER = 'X-XSRF-TOKEN';
const CSRF_TOKEN = 'mvp-mock-csrf-token';

type ConversationFixture = {
  code: string;
  message: string;
  data: ConversationResponse & {
    messages: Array<Record<string, unknown>>;
  };
};

const HISTORY_FIXTURES: Record<string, ConversationFixture> = {
  'conv-react-001': conversationWithReactHistory as ConversationFixture,
  'conv-plan-001': conversationWithPlanHistory as ConversationFixture,
  'conv-failed-001': conversationFailed as ConversationFixture,
  'conv-interrupted-001': conversationInterrupted as ConversationFixture,
};

export type MockSessionState = {
  authenticated: boolean;
  user: UserResponse | null;
  conversations: Map<string, ConversationListItem>;
  /** Force next mutating request to fail with CSRF_INVALID. */
  forceCsrfInvalid: boolean;
  /** Force next protected request to fail with ACCESS_DENIED. */
  forceAccessDenied: boolean;
  /** SSE scenario for Fake stream endpoint. */
  sseScenario: FakeSseScenario;
  /** Conversation ids that return CONVERSATION_BUSY on delete. */
  busyIds: Set<string>;
};

export function createInitialMockState(): MockSessionState {
  return {
    authenticated: false,
    user: null,
    conversations: new Map(),
    forceCsrfInvalid: false,
    forceAccessDenied: false,
    sseScenario: 'success-react',
    busyIds: new Set(),
  };
}

/** Mutable session used by handlers (reset in tests via resetMockState). */
export let mockState: MockSessionState = createInitialMockState();

export function resetMockState(partial?: Partial<MockSessionState>): void {
  mockState = { ...createInitialMockState(), ...partial };
  if (partial?.conversations) {
    mockState.conversations = partial.conversations;
  }
  if (partial?.busyIds) {
    mockState.busyIds = partial.busyIds;
  }
}

function ok<T>(data: T): ApiResponse<T> {
  return { code: 'OK', message: 'success', data };
}

function errorBody(code: string, message: string, data: unknown = null) {
  return { code, message, data };
}

function requireCsrf(request: Request): Response | null {
  if (mockState.forceCsrfInvalid) {
    mockState.forceCsrfInvalid = false;
    return HttpResponse.json(
      errorBody('CSRF_INVALID', 'CSRF token invalid or missing'),
      { status: 403 },
    );
  }
  const token = request.headers.get(CSRF_HEADER);
  if (!token || token !== CSRF_TOKEN) {
    return HttpResponse.json(
      errorBody('CSRF_INVALID', 'CSRF token invalid or missing'),
      { status: 403 },
    );
  }
  return null;
}

function requireAuth(): Response | null {
  if (mockState.forceAccessDenied) {
    mockState.forceAccessDenied = false;
    return HttpResponse.json(
      errorBody('ACCESS_DENIED', 'Access denied'),
      { status: 403 },
    );
  }
  if (!mockState.authenticated || !mockState.user) {
    return HttpResponse.json(
      errorBody('AUTH_REQUIRED', 'Authentication required'),
      { status: 401 },
    );
  }
  return null;
}

function seedHistoryListItems(): void {
  for (const [id, fixture] of Object.entries(HISTORY_FIXTURES)) {
    if (mockState.conversations.has(id)) continue;
    const { messages: _messages, ...rest } = fixture.data;
    mockState.conversations.set(id, {
      ...rest,
      lastMessagePreview: null,
    });
  }
}

export const handlers: HttpHandler[] = [
  http.get('/api/v1/auth/csrf', () => {
    return HttpResponse.json(
      ok({
        headerName: CSRF_HEADER,
        parameterName: '_csrf',
        token: CSRF_TOKEN,
      }),
    );
  }),

  http.post('/api/v1/auth/login', async ({ request }) => {
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const body = (await request.json()) as {
      username?: string;
      password?: string;
    };

    if (body.username === 'user-a' && body.password === 'password') {
      const user = (authSuccess as ApiResponse<UserResponse>).data!;
      mockState.authenticated = true;
      mockState.user = user;
      return HttpResponse.json(authSuccess);
    }

    return HttpResponse.json(auth401, { status: 401 });
  }),

  http.post('/api/v1/auth/logout', ({ request }) => {
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    mockState.authenticated = false;
    mockState.user = null;
    return HttpResponse.json(ok(null));
  }),

  http.get('/api/v1/users/me', () => {
    if (!mockState.authenticated || !mockState.user) {
      return HttpResponse.json(
        errorBody('AUTH_REQUIRED', 'Authentication required'),
        { status: 401 },
      );
    }
    if (mockState.forceAccessDenied) {
      mockState.forceAccessDenied = false;
      return HttpResponse.json(
        errorBody('ACCESS_DENIED', 'Access denied'),
        { status: 403 },
      );
    }
    return HttpResponse.json(ok(mockState.user));
  }),

  http.get('/api/v1/conversations', ({ request }) => {
    const authError = requireAuth();
    if (authError) return authError;

    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') || '1');
    const pageSize = Number(url.searchParams.get('pageSize') || '20');

    seedHistoryListItems();
    const all = Array.from(mockState.conversations.values());
    if (all.length === 0) {
      return HttpResponse.json(emptyConversations);
    }

    const start = (page - 1) * pageSize;
    const slice = all.slice(start, start + pageSize);
    const hasMore = start + pageSize < all.length;

    return HttpResponse.json(
      ok({
        items: slice,
        page,
        pageSize,
        hasMore,
      }),
    );
  }),

  http.post('/api/v1/conversations', async ({ request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const body = (await request.json().catch(() => ({}))) as {
      title?: string | null;
    };
    const now = new Date().toISOString();
    const id = `conv-${crypto.randomUUID()}`;
    const item: ConversationListItem = {
      id,
      title: body.title?.trim() || '新对话',
      lastMessageAt: null,
      createdAt: now,
      updatedAt: now,
      lastMessagePreview: null,
    };
    mockState.conversations.set(id, item);
    const { lastMessagePreview: _preview, ...response } = item;
    return HttpResponse.json(ok(response));
  }),

  http.get('/api/v1/conversations/:id', ({ params }) => {
    const authError = requireAuth();
    if (authError) return authError;

    const id = String(params.id);
    const history = HISTORY_FIXTURES[id];
    if (history) {
      const { messages: _messages, ...rest } = history.data;
      return HttpResponse.json(ok(rest));
    }

    const item = mockState.conversations.get(id);
    if (!item) {
      return HttpResponse.json(userIsolation404, { status: 404 });
    }
    const { lastMessagePreview: _preview, ...response } = item;
    return HttpResponse.json(ok(response));
  }),

  http.get('/api/v1/conversations/:id/messages', ({ params }) => {
    const authError = requireAuth();
    if (authError) return authError;

    const id = String(params.id);
    const history = HISTORY_FIXTURES[id];
    if (history) {
      return HttpResponse.json(ok(history.data.messages));
    }

    if (!mockState.conversations.has(id)) {
      return HttpResponse.json(userIsolation404, { status: 404 });
    }
    return HttpResponse.json(ok([]));
  }),

  http.patch('/api/v1/conversations/:id', async ({ params, request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const id = String(params.id);
    const item = mockState.conversations.get(id);
    if (!item && !HISTORY_FIXTURES[id]) {
      return HttpResponse.json(userIsolation404, { status: 404 });
    }

    const body = (await request.json()) as { title: string };
    const now = new Date().toISOString();
    const base =
      item ??
      ({
        id,
        title: HISTORY_FIXTURES[id]!.data.title,
        lastMessageAt: HISTORY_FIXTURES[id]!.data.lastMessageAt,
        createdAt: HISTORY_FIXTURES[id]!.data.createdAt,
        updatedAt: HISTORY_FIXTURES[id]!.data.updatedAt,
        lastMessagePreview: null,
      } satisfies ConversationListItem);

    const updated: ConversationListItem = {
      ...base,
      title: body.title,
      updatedAt: now,
    };
    mockState.conversations.set(id, updated);
    const { lastMessagePreview: _preview, ...response } = updated;
    return HttpResponse.json(ok(response));
  }),

  http.delete('/api/v1/conversations/:id', ({ params, request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const id = String(params.id);
    if (mockState.busyIds.has(id)) {
      return HttpResponse.json(conversationBusy, { status: 409 });
    }

    if (!mockState.conversations.has(id) && !HISTORY_FIXTURES[id]) {
      return HttpResponse.json(userIsolation404, { status: 404 });
    }

    mockState.conversations.delete(id);
    return HttpResponse.json(ok(null));
  }),

  http.post('/web/api/v1/gpt/queryAgentStreamIncr', ({ request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    return createFakeSseResponse(mockState.sseScenario);
  }),
];
