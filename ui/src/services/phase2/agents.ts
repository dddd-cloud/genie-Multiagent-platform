import type { PageResponse } from '@/contracts';
import type {
  Phase2AgentResponse,
  Phase2McpToolResponse,
  Phase2ModelResponse,
} from '@/contracts/phase2';
import {
  phase2Delete,
  phase2Get,
  phase2Post,
  phase2PostWithTimeout,
  phase2Put,
} from './client';
import type {
  AgentCreateRequest,
  AgentSkillBindingWire,
  AgentTestRequest,
  AgentTestResponse,
  AgentUpdateRequest,
  AgentWriteWire,
  Phase2VersionBody,
  PromptPreviewRequest,
  PromptPreviewResponse,
  PromptPreviewWireRequest,
  PromptPreviewWireResponse,
  ToolCapabilityItem,
} from './internalTypes';

const AGENTS_BASE = '/api/v2/agents';

function toSkillBindings(skillIds: string[]): AgentSkillBindingWire[] {
  return skillIds.map((skillId, index) => ({
    skillId,
    sortOrder: index + 1,
  }));
}

function toWirePromptConfig(
  promptConfig: Record<string, unknown> | null,
): string | null {
  if (promptConfig == null) return null;
  return JSON.stringify(promptConfig);
}

function toAgentWriteWire(
  body: AgentCreateRequest | AgentUpdateRequest,
): AgentWriteWire {
  const wire: AgentWriteWire = {
    name: body.name,
    description: body.description,
    promptMode: body.promptMode,
    promptConfig: toWirePromptConfig(body.promptConfig),
    systemPrompt: body.systemPrompt,
    modelName: body.modelName,
    skills: toSkillBindings(body.skillIds),
    capabilityKeys: body.capabilityKeys,
  };
  if ('version' in body) {
    wire.version = body.version;
  }
  return wire;
}

function toPromptPreviewWire(
  body: PromptPreviewRequest,
): PromptPreviewWireRequest {
  return {
    promptMode: body.promptMode,
    promptConfig: toWirePromptConfig(body.promptConfig),
    systemPrompt: body.systemPrompt,
    modelName: body.modelName ?? null,
    skills: toSkillBindings(body.skillIds),
  };
}

/** Builtin keys from CapabilityKeys — backend /tool-capabilities only returns MCP tools. */
const BUILTIN_CAPABILITIES: ToolCapabilityItem[] = [
  {
    key: 'builtin:code_interpreter',
    displayName: 'builtin:code_interpreter',
    available: true,
  },
  {
    key: 'builtin:data_analysis',
    displayName: 'builtin:data_analysis',
    available: true,
  },
  {
    key: 'builtin:deep_search',
    displayName: 'builtin:deep_search',
    available: true,
  },
  {
    key: 'builtin:file',
    displayName: 'builtin:file',
    available: true,
  },
  {
    key: 'builtin:report',
    displayName: 'builtin:report',
    available: true,
  },
];

function mapToolCapability(tool: Phase2McpToolResponse): ToolCapabilityItem {
  // Backend CapabilityKeys require mcp:<toolId>, not runtimeName.
  const toolId = tool.id?.trim();
  return {
    key: toolId ? `mcp:${toolId}` : '',
    displayName: tool.toolName ? `MCP · ${tool.toolName}` : `MCP · ${toolId}`,
    available: Boolean(tool.available && toolId),
  };
}

export async function listAgents(
  signal?: AbortSignal,
  page = 1,
  pageSize = 100,
) {
  const pageResponse = await phase2Get<PageResponse<Phase2AgentResponse>>(
    AGENTS_BASE,
    {
      page,
      pageSize,
    },
    signal,
  );
  return pageResponse?.items ?? [];
}

export function getAgent(id: string, signal?: AbortSignal) {
  return phase2Get<Phase2AgentResponse>(`${AGENTS_BASE}/${id}`, undefined, signal);
}

export function createAgent(body: AgentCreateRequest, signal?: AbortSignal) {
  return phase2Post<Phase2AgentResponse>(
    AGENTS_BASE,
    toAgentWriteWire(body),
    signal,
  );
}

export function updateAgent(
  id: string,
  body: AgentUpdateRequest,
  signal?: AbortSignal,
) {
  return phase2Put<Phase2AgentResponse>(
    `${AGENTS_BASE}/${id}`,
    toAgentWriteWire(body),
    signal,
  );
}

export function deleteAgent(
  id: string,
  body: Phase2VersionBody,
  signal?: AbortSignal,
) {
  return phase2Delete<null>(`${AGENTS_BASE}/${id}`, body, signal);
}

export function onlineAgent(
  id: string,
  body: Phase2VersionBody,
  signal?: AbortSignal,
) {
  return phase2Post<Phase2AgentResponse>(
    `${AGENTS_BASE}/${id}/online`,
    body,
    signal,
  );
}

export function offlineAgent(
  id: string,
  body: Phase2VersionBody,
  signal?: AbortSignal,
) {
  return phase2Post<Phase2AgentResponse>(
    `${AGENTS_BASE}/${id}/offline`,
    body,
    signal,
  );
}

export function testAgent(
  id: string,
  body: AgentTestRequest,
  signal?: AbortSignal,
) {
  return phase2PostWithTimeout<AgentTestResponse>(
    `${AGENTS_BASE}/${id}/test`,
    body,
    120_000,
    signal,
  );
}

export async function previewPrompt(
  body: PromptPreviewRequest,
  signal?: AbortSignal,
): Promise<PromptPreviewResponse | null> {
  const wire = await phase2Post<PromptPreviewWireResponse>(
    `${AGENTS_BASE}/prompt-preview`,
    toPromptPreviewWire(body),
    signal,
  );
  if (!wire) return null;
  return {
    preview: wire.compiledSystemPromptTemplate ?? '',
    skillFragments: wire.skillFragments,
    resolvedModelName: wire.resolvedModelName,
    codePointLength: wire.codePointLength,
  };
}

export function listModels(signal?: AbortSignal) {
  return phase2Get<Phase2ModelResponse[]>('/api/v2/models', undefined, signal);
}

export async function listToolCapabilities(signal?: AbortSignal) {
  const tools = await phase2Get<Phase2McpToolResponse[]>(
    '/api/v2/tool-capabilities',
    undefined,
    signal,
  );
  const mcpItems = (tools ?? [])
    .map(mapToolCapability)
    .filter((item) => item.key.startsWith('mcp:'));
  const seen = new Set(BUILTIN_CAPABILITIES.map((c) => c.key));
  const merged = [...BUILTIN_CAPABILITIES];
  for (const item of mcpItems) {
    if (!seen.has(item.key)) {
      seen.add(item.key);
      merged.push(item);
    }
  }
  return merged;
}
