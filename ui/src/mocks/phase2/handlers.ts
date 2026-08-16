import { http, HttpResponse, type HttpHandler } from 'msw';
import type {
  ApiResponse,
  ConversationMessageResponse,
  PageResponse,
} from '@/contracts';
import type {
  Phase2AgentResponse,
  Phase2McpServerResponse,
  Phase2McpToolResponse,
  Phase2SkillResponse,
} from '@/contracts/phase2';
import { mockState } from '../../../mocks/handlers';
import agentVersionConflict from './fixtures/agent-version-conflict.json';
import skillInUse from './fixtures/skill-in-use.json';
import mcpUrlRejected from './fixtures/mcp-url-rejected.json';
import mcpAuthInvalid from './fixtures/mcp-auth-invalid.json';
import mcpUnavailable from './fixtures/mcp-unavailable.json';
import mcpDiscoveryInvalid from './fixtures/mcp-discovery-invalid.json';
import memoryPatch from './fixtures/memory-patch.json';
import memorySummary from './fixtures/memory-summary.json';
import {
  assertNoCredentialEcho,
  assertNoForbiddenRequestFields,
} from './requestAssertions';
import {
  createFakePhase2SseResponse,
  extractFinalResponseContent,
  getPhase2NdjsonFixture,
} from './fakePhase2Sse';
import {
  getPhase2State,
  setPhase2Messages,
  type Phase2SseScenario,
} from './state';

const PHASE2_SSE_SCENARIOS: readonly Phase2SseScenario[] = [
  'direct-success',
  'direct-failure',
  'orchestrated-success',
  'orchestrated-replan',
  'orchestrated-summary-fallback',
] as const;

function isPhase2SseScenario(value: unknown): value is Phase2SseScenario {
  return (
    typeof value === 'string' &&
    (PHASE2_SSE_SCENARIOS as readonly string[]).includes(value)
  );
}

const CSRF_HEADER = 'X-XSRF-TOKEN';
const CSRF_TOKEN = 'mvp-mock-csrf-token';

function ok<T>(data: T): ApiResponse<T> {
  return {
    code: 'OK',
    message: 'success',
    data,
  };
}

function errorBody(code: string, message: string, data: unknown = null) {
  return {
    code,
    message,
    data,
  };
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

async function readJsonBody(request: Request): Promise<unknown> {
  try {
    return await request.json();
  } catch {
    return {};
  }
}

function forbiddenFieldsResponse(fieldPath: string): Response {
  return HttpResponse.json(
    errorBody('VALIDATION_ERROR', `Forbidden field in request: ${fieldPath}`),
    { status: 400 },
  );
}

function versionConflictResponse(): Response {
  return HttpResponse.json(agentVersionConflict, { status: 409 });
}

function nowIso(): string {
  return new Date().toISOString();
}

function bumpVersion(version: number): number {
  return version + 1;
}

function checkVersion(
  current: number,
  requested: unknown,
): Response | null {
  const state = getPhase2State();
  if (state.forceVersionConflict) {
    state.forceVersionConflict = false;
    return versionConflictResponse();
  }
  if (typeof requested !== 'number' || requested !== current) {
    return versionConflictResponse();
  }
  return null;
}

function isRejectedMcpUrl(url: string): boolean {
  try {
    const parsed = new URL(url);
    if (parsed.protocol !== 'https:') return true;
    const host = parsed.hostname.toLowerCase();
    return (
      host === 'localhost' ||
      host === '127.0.0.1' ||
      host.endsWith('.local') ||
      host === '0.0.0.0'
    );
  } catch {
    return true;
  }
}

function pageOf<T>(items: T[], page = 1, pageSize = 100): PageResponse<T> {
  const safePage = Number.isFinite(page) && page > 0 ? page : 1;
  const safeSize = Number.isFinite(pageSize) && pageSize > 0 ? pageSize : 100;
  const start = (safePage - 1) * safeSize;
  const slice = items.slice(start, start + safeSize);
  return {
    items: slice,
    page: safePage,
    pageSize: safeSize,
    hasMore: start + safeSize < items.length,
  };
}

function parsePromptConfig(
  value: unknown,
): Record<string, unknown> | null {
  if (value == null) return null;
  if (typeof value === 'string') {
    const trimmed = value.trim();
    if (!trimmed) return null;
    try {
      const parsed: unknown = JSON.parse(trimmed);
      if (
        parsed !== null &&
        typeof parsed === 'object' &&
        !Array.isArray(parsed)
      ) {
        return parsed as Record<string, unknown>;
      }
      return null;
    } catch {
      return null;
    }
  }
  if (typeof value === 'object' && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }
  return null;
}

function extractSkillIds(body: Record<string, unknown>): string[] | null {
  if (Array.isArray(body.skills)) {
    return body.skills
      .map((item) => {
        if (item && typeof item === 'object' && 'skillId' in item) {
          return String((item as { skillId: unknown }).skillId);
        }
        return '';
      })
      .filter(Boolean);
  }
  if (Array.isArray(body.skillIds)) {
    return body.skillIds.map(String);
  }
  return null;
}

function readVersionFromRequest(
  request: Request,
  body: Record<string, unknown>,
): unknown {
  const url = new URL(request.url);
  const queryVersion = url.searchParams.get('version');
  if (queryVersion != null && queryVersion !== '') {
    const parsed = Number(queryVersion);
    return Number.isFinite(parsed) ? parsed : queryVersion;
  }
  return body.version;
}

function buildPromptPreview(body: {
  promptMode?: string;
  systemPrompt?: string;
  promptConfig?: unknown;
  skillIds?: string[];
  skills?: Array<{ skillId?: string }>;
  name?: string;
}): string {
  const mode = body.promptMode ?? 'STRUCTURED';
  const name = body.name ?? 'Agent';
  const systemPrompt = body.systemPrompt ?? '';
  const skillIds =
    body.skillIds ??
    (Array.isArray(body.skills)
      ? body.skills.map((s) => String(s.skillId ?? '')).filter(Boolean)
      : []);
  if (mode === 'RAW') {
    return `[RAW:${name}]\n${systemPrompt}`;
  }
  const config = parsePromptConfig(body.promptConfig) ?? {};
  const configLines = Object.entries(config)
    .map(([k, v]) => `${k}=${String(v)}`)
    .join('; ');
  return `[STRUCTURED:${name}] ${configLines}\n${systemPrompt}\nskills=${skillIds.join(',')}`;
}

function deriveToolCapabilities(): Phase2McpToolResponse[] {
  const state = getPhase2State();
  const byKey = new Map<string, Phase2McpToolResponse>();
  for (const tools of state.mcpTools.values()) {
    for (const tool of tools) {
      const key = tool.runtimeName || tool.toolName || tool.id;
      byKey.set(key, structuredClone(tool));
    }
  }
  // Also surface capability keys from agents/skills so editor pickers stay useful.
  const extraKeys = new Set<string>();
  for (const agent of state.agents.values()) {
    for (const key of agent.capabilityKeys) extraKeys.add(key);
  }
  for (const skill of state.skills.values()) {
    for (const key of skill.capabilityKeys) extraKeys.add(key);
  }
  for (const key of extraKeys) {
    if (byKey.has(key)) continue;
    byKey.set(key, {
      id: `cap-${key}`,
      toolName: key,
      runtimeName: key,
      description: key,
      inputSchema: {},
      enabled: true,
      available: true,
      version: 1,
    });
  }
  return Array.from(byKey.values()).sort((a, b) =>
    (a.runtimeName || a.toolName).localeCompare(b.runtimeName || b.toolName),
  );
}

function publicMcpServer(server: Phase2McpServerResponse): Phase2McpServerResponse {
  return structuredClone(server);
}

export const phase2Handlers: HttpHandler[] = [
  /** Test-only mock control: set Phase2 SSE fixture scenario. */
  http.post('/api/v2/_test/sse-scenario', async ({ request }) => {
    const body = (await readJsonBody(request)) as { scenario?: unknown };
    if (!isPhase2SseScenario(body.scenario)) {
      return HttpResponse.json(
        errorBody('VALIDATION_ERROR', 'Invalid phase2 SSE scenario'),
        { status: 400 },
      );
    }
    getPhase2State().phase2SseScenario = body.scenario;
    return HttpResponse.json(ok({ scenario: body.scenario }));
  }),

  /** Test-only mock control: force next version conflict / other flags. */
  http.post('/api/v2/_test/flags', async ({ request }) => {
    const body = (await readJsonBody(request)) as {
      forceVersionConflict?: boolean;
      forceSkillInUse?: boolean;
      forceMcpError?: boolean;
    };
    const state = getPhase2State();
    if (typeof body.forceVersionConflict === 'boolean') {
      state.forceVersionConflict = body.forceVersionConflict;
    }
    if (typeof body.forceSkillInUse === 'boolean') {
      state.forceSkillInUse = body.forceSkillInUse;
    }
    if (typeof body.forceMcpError === 'boolean') {
      state.forceMcpError = body.forceMcpError;
    }
    return HttpResponse.json(
      ok({
        forceVersionConflict: state.forceVersionConflict,
        forceSkillInUse: state.forceSkillInUse,
        forceMcpError: state.forceMcpError,
      }),
    );
  }),

  http.get('/api/v2/agents', ({ request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const state = getPhase2State();
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? '1');
    const pageSize = Number(url.searchParams.get('pageSize') ?? '100');
    return HttpResponse.json(
      ok(pageOf(Array.from(state.agents.values()), page, pageSize)),
    );
  }),

  http.get('/api/v2/agents/:id', ({ params }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const state = getPhase2State();
    const agent = state.agents.get(String(params.id));
    if (!agent) {
      return HttpResponse.json(
        errorBody('RESOURCE_NOT_FOUND', 'Agent not found'),
        { status: 404 },
      );
    }
    return HttpResponse.json(ok(agent));
  }),

  http.post('/api/v2/agents', async ({ request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const body = (await readJsonBody(request)) as Record<string, unknown>;
    const forbidden = assertNoForbiddenRequestFields(body);
    if (forbidden) return forbiddenFieldsResponse(forbidden);

    const now = nowIso();
    const skillIds = extractSkillIds(body) ?? [];
    const agent: Phase2AgentResponse = {
      id: `agent-${crypto.randomUUID()}`,
      name: String(body.name ?? ''),
      description: String(body.description ?? ''),
      promptMode: (body.promptMode as Phase2AgentResponse['promptMode']) ?? 'STRUCTURED',
      promptConfig: parsePromptConfig(body.promptConfig),
      systemPrompt: String(body.systemPrompt ?? ''),
      modelName: (body.modelName as string | null) ?? null,
      status: 'DRAFT',
      version: 0,
      skillIds,
      capabilityKeys: Array.isArray(body.capabilityKeys)
        ? (body.capabilityKeys as string[])
        : [],
      createdAt: now,
      updatedAt: now,
    };
    getPhase2State().agents.set(agent.id, agent);
    return HttpResponse.json(ok(agent));
  }),

  http.put('/api/v2/agents/:id', async ({ params, request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const body = (await readJsonBody(request)) as Record<string, unknown>;
    const forbidden = assertNoForbiddenRequestFields(body);
    if (forbidden) return forbiddenFieldsResponse(forbidden);

    const state = getPhase2State();
    const id = String(params.id);
    const existing = state.agents.get(id);
    if (!existing) {
      return HttpResponse.json(
        errorBody('RESOURCE_NOT_FOUND', 'Agent not found'),
        { status: 404 },
      );
    }
    const versionError = checkVersion(existing.version, body.version);
    if (versionError) return versionError;

    const skillIds = extractSkillIds(body);
    const updated: Phase2AgentResponse = {
      ...existing,
      name: String(body.name ?? existing.name),
      description: String(body.description ?? existing.description),
      promptMode:
        (body.promptMode as Phase2AgentResponse['promptMode']) ??
        existing.promptMode,
      promptConfig:
        body.promptConfig === undefined
          ? existing.promptConfig
          : parsePromptConfig(body.promptConfig),
      systemPrompt: String(body.systemPrompt ?? existing.systemPrompt),
      modelName:
        body.modelName === undefined
          ? existing.modelName
          : (body.modelName as string | null),
      skillIds: skillIds ?? existing.skillIds,
      capabilityKeys: Array.isArray(body.capabilityKeys)
        ? (body.capabilityKeys as string[])
        : existing.capabilityKeys,
      version: bumpVersion(existing.version),
      updatedAt: nowIso(),
    };
    state.agents.set(id, updated);
    return HttpResponse.json(ok(updated));
  }),

  http.delete('/api/v2/agents/:id', async ({ params, request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const body = (await readJsonBody(request)) as Record<string, unknown>;
    const forbidden = assertNoForbiddenRequestFields(body);
    if (forbidden) return forbiddenFieldsResponse(forbidden);

    const state = getPhase2State();
    const id = String(params.id);
    const existing = state.agents.get(id);
    if (!existing) {
      return HttpResponse.json(
        errorBody('RESOURCE_NOT_FOUND', 'Agent not found'),
        { status: 404 },
      );
    }
    if (existing.status === 'ONLINE') {
      return HttpResponse.json(
        errorBody('AGENT_MUST_BE_OFFLINE', 'Agent must be offline'),
        { status: 409 },
      );
    }
    const versionError = checkVersion(existing.version, body.version);
    if (versionError) return versionError;

    state.agents.delete(id);
    return HttpResponse.json(ok(null));
  }),

  http.post('/api/v2/agents/:id/online', async ({ params, request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const body = (await readJsonBody(request)) as Record<string, unknown>;
    const forbidden = assertNoForbiddenRequestFields(body);
    if (forbidden) return forbiddenFieldsResponse(forbidden);

    const state = getPhase2State();
    const id = String(params.id);
    const existing = state.agents.get(id);
    if (!existing) {
      return HttpResponse.json(
        errorBody('RESOURCE_NOT_FOUND', 'Agent not found'),
        { status: 404 },
      );
    }
    if (existing.status === 'ONLINE') {
      return HttpResponse.json(
        errorBody('AGENT_INVALID_STATE', 'Agent already online'),
        { status: 409 },
      );
    }
    const versionError = checkVersion(existing.version, body.version);
    if (versionError) return versionError;

    const updated: Phase2AgentResponse = {
      ...existing,
      status: 'ONLINE',
      version: bumpVersion(existing.version),
      updatedAt: nowIso(),
    };
    state.agents.set(id, updated);
    return HttpResponse.json(ok(updated));
  }),

  http.post('/api/v2/agents/:id/offline', async ({ params, request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const body = (await readJsonBody(request)) as Record<string, unknown>;
    const forbidden = assertNoForbiddenRequestFields(body);
    if (forbidden) return forbiddenFieldsResponse(forbidden);

    const state = getPhase2State();
    const id = String(params.id);
    const existing = state.agents.get(id);
    if (!existing) {
      return HttpResponse.json(
        errorBody('RESOURCE_NOT_FOUND', 'Agent not found'),
        { status: 404 },
      );
    }
    const versionError = checkVersion(existing.version, body.version);
    if (versionError) return versionError;

    const updated: Phase2AgentResponse = {
      ...existing,
      status: 'OFFLINE',
      version:
        existing.status === 'OFFLINE'
          ? existing.version
          : bumpVersion(existing.version),
      updatedAt: nowIso(),
    };
    state.agents.set(id, updated);
    return HttpResponse.json(ok(updated));
  }),

  http.post('/api/v2/agents/:id/test', async ({ params, request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const body = (await readJsonBody(request)) as Record<string, unknown>;
    const forbidden = assertNoForbiddenRequestFields(body);
    if (forbidden) return forbiddenFieldsResponse(forbidden);

    const state = getPhase2State();
    const agent = state.agents.get(String(params.id));
    if (!agent) {
      return HttpResponse.json(
        errorBody('RESOURCE_NOT_FOUND', 'Agent not found'),
        { status: 404 },
      );
    }
    if (agent.status !== 'ONLINE') {
      return HttpResponse.json(
        errorBody('AGENT_OFFLINE', 'Agent must be online for test'),
        { status: 409 },
      );
    }
    const compiled = `Test OK for ${agent.name}`;
    return HttpResponse.json(
      ok({
        model: agent.modelName,
        skillSummary: agent.skillIds,
        capabilityKeys: agent.capabilityKeys,
        result: {
          status: 'SUCCESS',
          output: compiled,
          errorCode: null,
          retryable: false,
        },
        elapsedMillis: 12,
        progressEventCount: 1,
      }),
    );
  }),

  http.post('/api/v2/agents/prompt-preview', async ({ request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const body = (await readJsonBody(request)) as {
      promptMode?: string;
      systemPrompt?: string;
      promptConfig?: unknown;
      skillIds?: string[];
      skills?: Array<{ skillId?: string }>;
      name?: string;
      modelName?: string | null;
    };
    const forbidden = assertNoForbiddenRequestFields(body);
    if (forbidden) return forbiddenFieldsResponse(forbidden);

    const compiled = buildPromptPreview(body);
    return HttpResponse.json(
      ok({
        compiledSystemPromptTemplate: compiled,
        skillFragments: (body.skills ?? []).map((skill, index) => ({
          skillId: String(skill.skillId ?? ''),
          skillVersion: 1,
          sortOrder: index + 1,
        })),
        resolvedModelName: body.modelName ?? null,
        codePointLength: [...compiled].length,
      }),
    );
  }),

  http.get('/api/v2/models', () => {
    const authError = requireAuth();
    if (authError) return authError;
    return HttpResponse.json(ok(getPhase2State().models));
  }),

  http.get('/api/v2/tool-capabilities', () => {
    const authError = requireAuth();
    if (authError) return authError;
    return HttpResponse.json(ok(deriveToolCapabilities()));
  }),

  http.get('/api/v2/skills', ({ request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? '1');
    const pageSize = Number(url.searchParams.get('pageSize') ?? '100');
    return HttpResponse.json(
      ok(pageOf(Array.from(getPhase2State().skills.values()), page, pageSize)),
    );
  }),

  http.get('/api/v2/skills/:id', ({ params }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const skill = getPhase2State().skills.get(String(params.id));
    if (!skill) {
      return HttpResponse.json(
        errorBody('RESOURCE_NOT_FOUND', 'Skill not found'),
        { status: 404 },
      );
    }
    return HttpResponse.json(ok(skill));
  }),

  http.post('/api/v2/skills', async ({ request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const body = (await readJsonBody(request)) as Record<string, unknown>;
    const forbidden = assertNoForbiddenRequestFields(body);
    if (forbidden) return forbiddenFieldsResponse(forbidden);

    const now = nowIso();
    const skill: Phase2SkillResponse = {
      id: `skill-${crypto.randomUUID()}`,
      name: String(body.name ?? ''),
      description: String(body.description ?? ''),
      instruction: String(body.instruction ?? ''),
      outputRequirement: String(body.outputRequirement ?? ''),
      status: 'ENABLED',
      version: 0,
      capabilityKeys: Array.isArray(body.capabilityKeys)
        ? (body.capabilityKeys as string[])
        : [],
      createdAt: now,
      updatedAt: now,
    };
    getPhase2State().skills.set(skill.id, skill);
    return HttpResponse.json(ok(skill));
  }),

  http.post('/api/v2/skills/import', async ({ request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const form = await request.formData();
    const file = form.get('file');
    const skillIdRaw = form.get('skillId');
    if (!(file instanceof File) || file.size === 0) {
      return HttpResponse.json(
        errorBody('SKILL_PACKAGE_INVALID', 'zip required'),
        { status: 422 },
      );
    }
    if (file.size > 10 * 1024 * 1024) {
      return HttpResponse.json(
        errorBody('SKILL_PACKAGE_INVALID', 'zip too large'),
        { status: 422 },
      );
    }
    const now = nowIso();
    const skillId =
      typeof skillIdRaw === 'string' && skillIdRaw.trim()
        ? skillIdRaw.trim()
        : `skill-${crypto.randomUUID()}`;
    const existing = getPhase2State().skills.get(skillId);
    const skill: Phase2SkillResponse = {
      id: skillId,
      name: existing?.name || file.name.replace(/\.zip$/i, '') || 'Imported Skill',
      description: existing?.description || 'Imported skill package',
      instruction: existing?.instruction || 'Imported from SKILL.md',
      outputRequirement: existing?.outputRequirement || '',
      status: existing?.status || 'ENABLED',
      version: existing ? existing.version + 1 : 0,
      capabilityKeys: existing?.capabilityKeys ?? [],
      createdAt: existing?.createdAt || now,
      updatedAt: now,
      packageMode: 'FILESYSTEM',
    };
    getPhase2State().skills.set(skill.id, skill);
    return HttpResponse.json(ok(skill));
  }),

  http.put('/api/v2/skills/:id', async ({ params, request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const body = (await readJsonBody(request)) as Record<string, unknown>;
    const forbidden = assertNoForbiddenRequestFields(body);
    if (forbidden) return forbiddenFieldsResponse(forbidden);

    const state = getPhase2State();
    const id = String(params.id);
    const existing = state.skills.get(id);
    if (!existing) {
      return HttpResponse.json(
        errorBody('RESOURCE_NOT_FOUND', 'Skill not found'),
        { status: 404 },
      );
    }
    const versionError = checkVersion(existing.version, body.version);
    if (versionError) return versionError;

    const updated: Phase2SkillResponse = {
      ...existing,
      name: String(body.name ?? existing.name),
      description: String(body.description ?? existing.description),
      instruction: String(body.instruction ?? existing.instruction),
      outputRequirement: String(
        body.outputRequirement ?? existing.outputRequirement,
      ),
      capabilityKeys: Array.isArray(body.capabilityKeys)
        ? (body.capabilityKeys as string[])
        : existing.capabilityKeys,
      version: bumpVersion(existing.version),
      updatedAt: nowIso(),
    };
    state.skills.set(id, updated);
    return HttpResponse.json(ok(updated));
  }),

  http.delete('/api/v2/skills/:id', async ({ params, request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const body = (await readJsonBody(request)) as Record<string, unknown>;
    const forbidden = assertNoForbiddenRequestFields(body);
    if (forbidden) return forbiddenFieldsResponse(forbidden);

    const state = getPhase2State();
    const id = String(params.id);
    const existing = state.skills.get(id);
    if (!existing) {
      return HttpResponse.json(
        errorBody('RESOURCE_NOT_FOUND', 'Skill not found'),
        { status: 404 },
      );
    }
    if (state.forceSkillInUse) {
      state.forceSkillInUse = false;
      return HttpResponse.json(skillInUse, { status: 409 });
    }
    const inUse = Array.from(state.agents.values()).some((agent) =>
      agent.skillIds.includes(id),
    );
    if (inUse) {
      return HttpResponse.json(skillInUse, { status: 409 });
    }
    const versionError = checkVersion(existing.version, body.version);
    if (versionError) return versionError;

    state.skills.delete(id);
    return HttpResponse.json(ok(null));
  }),

  http.post('/api/v2/skills/:id/enable', async ({ params, request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const body = (await readJsonBody(request)) as Record<string, unknown>;
    const forbidden = assertNoForbiddenRequestFields(body);
    if (forbidden) return forbiddenFieldsResponse(forbidden);

    const state = getPhase2State();
    const id = String(params.id);
    const existing = state.skills.get(id);
    if (!existing) {
      return HttpResponse.json(
        errorBody('RESOURCE_NOT_FOUND', 'Skill not found'),
        { status: 404 },
      );
    }
    const versionError = checkVersion(existing.version, body.version);
    if (versionError) return versionError;

    const updated: Phase2SkillResponse = {
      ...existing,
      status: 'ENABLED',
      version: bumpVersion(existing.version),
      updatedAt: nowIso(),
    };
    state.skills.set(id, updated);
    return HttpResponse.json(ok(updated));
  }),

  http.post('/api/v2/skills/:id/disable', async ({ params, request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const body = (await readJsonBody(request)) as Record<string, unknown>;
    const forbidden = assertNoForbiddenRequestFields(body);
    if (forbidden) return forbiddenFieldsResponse(forbidden);

    const state = getPhase2State();
    const id = String(params.id);
    const existing = state.skills.get(id);
    if (!existing) {
      return HttpResponse.json(
        errorBody('RESOURCE_NOT_FOUND', 'Skill not found'),
        { status: 404 },
      );
    }
    const versionError = checkVersion(existing.version, body.version);
    if (versionError) return versionError;

    const updated: Phase2SkillResponse = {
      ...existing,
      status: 'DISABLED',
      version: bumpVersion(existing.version),
      updatedAt: nowIso(),
    };
    state.skills.set(id, updated);
    return HttpResponse.json(ok(updated));
  }),

  http.get('/api/v2/mcp-servers', () => {
    const authError = requireAuth();
    if (authError) return authError;
    return HttpResponse.json(
      ok(Array.from(getPhase2State().mcpServers.values()).map(publicMcpServer)),
    );
  }),

  http.get('/api/v2/mcp-servers/:id', ({ params }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const server = getPhase2State().mcpServers.get(String(params.id));
    if (!server) {
      return HttpResponse.json(
        errorBody('RESOURCE_NOT_FOUND', 'MCP server not found'),
        { status: 404 },
      );
    }
    return HttpResponse.json(ok(publicMcpServer(server)));
  }),

  http.post('/api/v2/mcp-servers', async ({ request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const body = (await readJsonBody(request)) as Record<string, unknown>;
    const forbidden = assertNoForbiddenRequestFields(body);
    if (forbidden) return forbiddenFieldsResponse(forbidden);

    const state = getPhase2State();
    if (state.forceMcpError) {
      state.forceMcpError = false;
      return HttpResponse.json(mcpUnavailable, { status: 502 });
    }

    const serverUrl = String(body.serverUrl ?? '');
    if (isRejectedMcpUrl(serverUrl)) {
      return HttpResponse.json(mcpUrlRejected, { status: 400 });
    }

    const credential =
      typeof body.credential === 'string' ? body.credential : undefined;
    const now = nowIso();
    const server: Phase2McpServerResponse = {
      id: `mcp-server-${crypto.randomUUID()}`,
      name: String(body.name ?? ''),
      serverUrl,
      authType: (body.authType as Phase2McpServerResponse['authType']) ?? 'NONE',
      authName: (body.authName as string | null) ?? null,
      status: 'DRAFT',
      credentialConfigured: Boolean(credential && credential.length > 0),
      lastCheckStatus: null,
      lastCheckCode: null,
      lastCheckedAt: null,
      version: 0,
      createdAt: now,
      updatedAt: now,
    };

    const responseBody = ok(publicMcpServer(server));
    const echo = assertNoCredentialEcho(responseBody, credential);
    if (echo) {
      return HttpResponse.json(
        errorBody('VALIDATION_ERROR', echo),
        { status: 400 },
      );
    }

    state.mcpServers.set(server.id, server);
    state.mcpTools.set(server.id, []);
    return HttpResponse.json(responseBody);
  }),

  http.put('/api/v2/mcp-servers/:id', async ({ params, request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const body = (await readJsonBody(request)) as Record<string, unknown>;
    const forbidden = assertNoForbiddenRequestFields(body);
    if (forbidden) return forbiddenFieldsResponse(forbidden);

    const state = getPhase2State();
    if (state.forceMcpError) {
      state.forceMcpError = false;
      return HttpResponse.json(mcpAuthInvalid, { status: 400 });
    }

    const id = String(params.id);
    const existing = state.mcpServers.get(id);
    if (!existing) {
      return HttpResponse.json(
        errorBody('RESOURCE_NOT_FOUND', 'MCP server not found'),
        { status: 404 },
      );
    }
    const versionError = checkVersion(existing.version, body.version);
    if (versionError) return versionError;

    const serverUrl = String(body.serverUrl ?? existing.serverUrl);
    if (isRejectedMcpUrl(serverUrl)) {
      return HttpResponse.json(mcpUrlRejected, { status: 400 });
    }

    const credential =
      typeof body.credential === 'string' ? body.credential : undefined;
    let credentialConfigured = existing.credentialConfigured;
    if (body.clearCredential === true) {
      credentialConfigured = false;
    } else if (credential && credential.length > 0) {
      credentialConfigured = true;
    }

    const updated: Phase2McpServerResponse = {
      ...existing,
      name: String(body.name ?? existing.name),
      serverUrl,
      authType:
        (body.authType as Phase2McpServerResponse['authType']) ??
        existing.authType,
      authName:
        body.authName === undefined
          ? existing.authName
          : (body.authName as string | null),
      credentialConfigured,
      version: bumpVersion(existing.version),
      updatedAt: nowIso(),
    };

    const responseBody = ok(publicMcpServer(updated));
    const echo = assertNoCredentialEcho(responseBody, credential);
    if (echo) {
      return HttpResponse.json(
        errorBody('VALIDATION_ERROR', echo),
        { status: 400 },
      );
    }

    state.mcpServers.set(id, updated);
    return HttpResponse.json(responseBody);
  }),

  http.delete('/api/v2/mcp-servers/:id', async ({ params, request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const body = (await readJsonBody(request)) as Record<string, unknown>;
    const forbidden = assertNoForbiddenRequestFields(body);
    if (forbidden) return forbiddenFieldsResponse(forbidden);

    const state = getPhase2State();
    const id = String(params.id);
    const existing = state.mcpServers.get(id);
    if (!existing) {
      return HttpResponse.json(
        errorBody('RESOURCE_NOT_FOUND', 'MCP server not found'),
        { status: 404 },
      );
    }
    if (existing.status === 'ENABLED') {
      return HttpResponse.json(
        errorBody('VALIDATION_ERROR', 'Disable MCP server before delete'),
        { status: 400 },
      );
    }
    const versionError = checkVersion(
      existing.version,
      readVersionFromRequest(request, body),
    );
    if (versionError) return versionError;

    state.mcpServers.delete(id);
    state.mcpTools.delete(id);
    return HttpResponse.json(ok(null));
  }),

  http.post('/api/v2/mcp-servers/:id/test', async ({ params, request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const body = (await readJsonBody(request)) as Record<string, unknown>;
    const forbidden = assertNoForbiddenRequestFields(body);
    if (forbidden) return forbiddenFieldsResponse(forbidden);

    const state = getPhase2State();
    if (state.forceMcpError) {
      state.forceMcpError = false;
      return HttpResponse.json(mcpUnavailable, { status: 502 });
    }

    const id = String(params.id);
    const existing = state.mcpServers.get(id);
    if (!existing) {
      return HttpResponse.json(
        errorBody('RESOURCE_NOT_FOUND', 'MCP server not found'),
        { status: 404 },
      );
    }
    // Real API does not require version; still accept legacy body version if present.
    if (body.version !== undefined) {
      const versionError = checkVersion(existing.version, body.version);
      if (versionError) return versionError;
    }

    const updated: Phase2McpServerResponse = {
      ...existing,
      lastCheckStatus: 'SUCCESS',
      lastCheckCode: 'OK',
      lastCheckedAt: nowIso(),
      version: bumpVersion(existing.version),
      updatedAt: nowIso(),
    };
    state.mcpServers.set(id, updated);
    return HttpResponse.json(ok(publicMcpServer(updated)));
  }),

  http.post('/api/v2/mcp-servers/:id/refresh-tools', async ({ params, request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const body = (await readJsonBody(request)) as Record<string, unknown>;
    const forbidden = assertNoForbiddenRequestFields(body);
    if (forbidden) return forbiddenFieldsResponse(forbidden);

    const state = getPhase2State();
    if (state.forceMcpError) {
      state.forceMcpError = false;
      return HttpResponse.json(mcpDiscoveryInvalid, { status: 400 });
    }

    const id = String(params.id);
    const existing = state.mcpServers.get(id);
    if (!existing) {
      return HttpResponse.json(
        errorBody('RESOURCE_NOT_FOUND', 'MCP server not found'),
        { status: 404 },
      );
    }
    if (body.version !== undefined) {
      const versionError = checkVersion(existing.version, body.version);
      if (versionError) return versionError;
    }

    const tools = state.mcpTools.get(id) ?? [];
    const refreshed = tools.map((tool) => ({
      ...tool,
      available: true,
      version: bumpVersion(tool.version),
    }));
    state.mcpTools.set(id, refreshed);
    state.mcpServers.set(id, {
      ...existing,
      version: bumpVersion(existing.version),
      updatedAt: nowIso(),
    });
    return HttpResponse.json(ok(refreshed));
  }),

  http.post('/api/v2/mcp-servers/:id/enable', async ({ params, request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const body = (await readJsonBody(request)) as Record<string, unknown>;
    const forbidden = assertNoForbiddenRequestFields(body);
    if (forbidden) return forbiddenFieldsResponse(forbidden);

    const state = getPhase2State();
    const id = String(params.id);
    const existing = state.mcpServers.get(id);
    if (!existing) {
      return HttpResponse.json(
        errorBody('RESOURCE_NOT_FOUND', 'MCP server not found'),
        { status: 404 },
      );
    }
    const versionError = checkVersion(
      existing.version,
      readVersionFromRequest(request, body),
    );
    if (versionError) return versionError;

    const updated: Phase2McpServerResponse = {
      ...existing,
      status: 'ENABLED',
      version: bumpVersion(existing.version),
      updatedAt: nowIso(),
    };
    state.mcpServers.set(id, updated);
    return HttpResponse.json(ok(publicMcpServer(updated)));
  }),

  http.post('/api/v2/mcp-servers/:id/disable', async ({ params, request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const body = (await readJsonBody(request)) as Record<string, unknown>;
    const forbidden = assertNoForbiddenRequestFields(body);
    if (forbidden) return forbiddenFieldsResponse(forbidden);

    const state = getPhase2State();
    const id = String(params.id);
    const existing = state.mcpServers.get(id);
    if (!existing) {
      return HttpResponse.json(
        errorBody('RESOURCE_NOT_FOUND', 'MCP server not found'),
        { status: 404 },
      );
    }
    const versionError = checkVersion(
      existing.version,
      readVersionFromRequest(request, body),
    );
    if (versionError) return versionError;

    const updated: Phase2McpServerResponse = {
      ...existing,
      status: 'DISABLED',
      version: bumpVersion(existing.version),
      updatedAt: nowIso(),
    };
    state.mcpServers.set(id, updated);
    return HttpResponse.json(ok(publicMcpServer(updated)));
  }),

  http.get('/api/v2/mcp-servers/:id/tools', ({ params }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const state = getPhase2State();
    const id = String(params.id);
    if (!state.mcpServers.has(id)) {
      return HttpResponse.json(
        errorBody('RESOURCE_NOT_FOUND', 'MCP server not found'),
        { status: 404 },
      );
    }
    return HttpResponse.json(ok(state.mcpTools.get(id) ?? []));
  }),

  http.put('/api/v2/mcp-servers/:id/tools/:toolId/enabled', async ({ params, request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const body = (await readJsonBody(request)) as Record<string, unknown>;
    const forbidden = assertNoForbiddenRequestFields(body);
    if (forbidden) return forbiddenFieldsResponse(forbidden);

    const state = getPhase2State();
    const serverId = String(params.id);
    const toolId = String(params.toolId);
    if (!state.mcpServers.has(serverId)) {
      return HttpResponse.json(
        errorBody('RESOURCE_NOT_FOUND', 'MCP server not found'),
        { status: 404 },
      );
    }
    const tools = state.mcpTools.get(serverId) ?? [];
    const index = tools.findIndex((tool) => tool.id === toolId);
    if (index < 0) {
      return HttpResponse.json(
        errorBody('RESOURCE_NOT_FOUND', 'MCP tool not found'),
        { status: 404 },
      );
    }
    const existing = tools[index];
    const versionError = checkVersion(existing.version, body.version);
    if (versionError) return versionError;

    const updated: Phase2McpToolResponse = {
      ...existing,
      enabled: Boolean(body.enabled),
      version: bumpVersion(existing.version),
    };
    const nextTools = [...tools];
    nextTools[index] = updated;
    state.mcpTools.set(serverId, nextTools);
    return HttpResponse.json(ok(updated));
  }),

  http.post('/api/v2/memory/analyze-turn', async ({ request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const body = await readJsonBody(request);
    const forbidden = assertNoForbiddenRequestFields(body);
    if (forbidden) return forbiddenFieldsResponse(forbidden);

    return HttpResponse.json(memoryPatch);
  }),

  http.post('/api/v2/memory/summarize-conversation', async ({ request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const body = await readJsonBody(request);
    const forbidden = assertNoForbiddenRequestFields(body);
    if (forbidden) return forbiddenFieldsResponse(forbidden);

    return HttpResponse.json(memorySummary);
  }),

  http.get('/api/v2/memory/status', () => {
    const authError = requireAuth();
    if (authError) return authError;
    return HttpResponse.json(
      ok({
        available: true,
        rootPath: 'C:/Users/mock/.joyagent/memory',
        userId: mockState.user?.id ?? 'mock-user',
      }),
    );
  }),

  http.get('/api/v2/memory/long-term', () => {
    const authError = requireAuth();
    if (authError) return authError;
    const markdown = getPhase2State().memoryLongTerm;
    if (markdown == null) {
      return HttpResponse.json(ok({ status: 'EMPTY', markdown: null, reason: null }));
    }
    return HttpResponse.json(ok({ status: 'READY', markdown, reason: null }));
  }),

  http.put('/api/v2/memory/long-term', async ({ request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;
    const body = (await readJsonBody(request)) as { markdown?: string };
    const markdown = typeof body.markdown === 'string' ? body.markdown : '';
    getPhase2State().memoryLongTerm = markdown;
    return HttpResponse.json(ok({ status: 'READY', markdown, reason: null }));
  }),

  http.delete('/api/v2/memory/long-term', async ({ request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;
    getPhase2State().memoryLongTerm = null;
    return HttpResponse.json(ok(null));
  }),

  http.get('/api/v2/memory/summaries', () => {
    const authError = requireAuth();
    if (authError) return authError;
    const userId = mockState.user?.id ?? 'mock-user';
    const items = [...getPhase2State().memorySummaries.entries()].map(
      ([conversationId, markdown]) => ({
        conversationId,
        path: `/memory/v1/users/${userId}/conversations/${conversationId}/对话摘要.md`,
        updatedAt: new Date().toISOString(),
        lastSummarizedTurnNo: null,
        markdown,
      }),
    );
    return HttpResponse.json(ok({ items: items.map(({ markdown: _markdown, ...item }) => item) }));
  }),

  http.get('/api/v2/memory/conversations/:conversationId/summary', ({ params }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const conversationId = String(params.conversationId ?? '');
    const markdown = getPhase2State().memorySummaries.get(conversationId) ?? null;
    if (markdown == null) {
      return HttpResponse.json(ok({ status: 'EMPTY', markdown: null, reason: null }));
    }
    return HttpResponse.json(ok({ status: 'READY', markdown, reason: null }));
  }),

  http.put(
    '/api/v2/memory/conversations/:conversationId/summary',
    async ({ request, params }) => {
      const authError = requireAuth();
      if (authError) return authError;
      const csrfError = requireCsrf(request);
      if (csrfError) return csrfError;
      const conversationId = String(params.conversationId ?? '');
      const body = (await readJsonBody(request)) as { markdown?: string };
      const markdown = typeof body.markdown === 'string' ? body.markdown : '';
      getPhase2State().memorySummaries.set(conversationId, markdown);
      return HttpResponse.json(ok({ status: 'READY', markdown, reason: null }));
    },
  ),

  http.delete(
    '/api/v2/memory/conversations/:conversationId/summary',
    async ({ request, params }) => {
      const authError = requireAuth();
      if (authError) return authError;
      const csrfError = requireCsrf(request);
      if (csrfError) return csrfError;
      getPhase2State().memorySummaries.delete(String(params.conversationId ?? ''));
      return HttpResponse.json(ok(null));
    },
  ),

  http.post('/web/api/v2/gpt/queryAgentStreamIncr', async ({ request }) => {
    const authError = requireAuth();
    if (authError) return authError;
    const csrfError = requireCsrf(request);
    if (csrfError) return csrfError;

    const body = (await readJsonBody(request)) as {
      sessionId?: string;
      requestId?: string;
      query?: string;
      steps?: unknown;
      inputRefs?: unknown;
      attemptNo?: unknown;
      ownerId?: unknown;
      tenantId?: unknown;
      userId?: unknown;
      traceId?: unknown;
    };
    const forbidden = assertNoForbiddenRequestFields(body);
    if (forbidden) return forbiddenFieldsResponse(forbidden);

    const state = getPhase2State();
    const conversationId = String(body.sessionId ?? '');
    const requestId = String(body.requestId ?? `req-${crypto.randomUUID()}`);
    const query = String(body.query ?? '');
    const now = nowIso();

    const userMessage: ConversationMessageResponse = {
      id: `msg-user-${crypto.randomUUID()}`,
      turnNo: 1,
      role: 'USER',
      status: 'COMPLETED',
      requestId,
      content: query,
      streamSnapshot: null,
      payloadVersion: 1,
      deepThink: null,
      outputStyle: null,
      errorCode: null,
      errorMessage: null,
      createdAt: now,
      updatedAt: now,
    };
    const assistantMessage: ConversationMessageResponse = {
      id: `msg-asst-${crypto.randomUUID()}`,
      turnNo: 1,
      role: 'ASSISTANT',
      status: 'STREAMING',
      requestId,
      content: null,
      streamSnapshot: null,
      payloadVersion: 1,
      deepThink: null,
      outputStyle: null,
      errorCode: null,
      errorMessage: null,
      createdAt: now,
      updatedAt: now,
    };

    if (conversationId) {
      const existing = state.conversationMessages.get(conversationId) ?? [];
      const turnNo =
        existing.filter((m) => m.role === 'USER').length + 1;
      userMessage.turnNo = turnNo;
      assistantMessage.turnNo = turnNo;
      setPhase2Messages(conversationId, [
        ...existing,
        userMessage,
        assistantMessage,
      ]);

      if (!mockState.conversations.has(conversationId)) {
        mockState.conversations.set(conversationId, {
          id: conversationId,
          title: query.slice(0, 32) || '新对话',
          privacyMode: false,
          lastMessageAt: now,
          createdAt: now,
          updatedAt: now,
          lastMessagePreview: null,
        });
      }
    }

    const raw = getPhase2NdjsonFixture(state.phase2SseScenario);
    const finalContent = extractFinalResponseContent(raw);
    const response = createFakePhase2SseResponse(state.phase2SseScenario);

    if (conversationId) {
      queueMicrotask(() => {
        const messages = getPhase2State().conversationMessages.get(conversationId);
        if (!messages) return;
        const next = messages.map((msg) => {
          if (msg.id !== assistantMessage.id) return msg;
          return {
            ...msg,
            status: 'COMPLETED' as const,
            content: finalContent,
            streamSnapshot: JSON.stringify({
              payloadVersion: 1,
              truncated: false,
              events: [],
            }),
            updatedAt: nowIso(),
          };
        });
        setPhase2Messages(conversationId, next);
      });
    }

    return response;
  }),
];
