import type { SkillEntrypointView } from './skill-runtime';

export const AGENT_PROMPT_MODES = ['STRUCTURED', 'RAW'] as const;
export type AgentPromptMode = (typeof AGENT_PROMPT_MODES)[number];

export const AGENT_STATUSES = ['DRAFT', 'ONLINE', 'OFFLINE'] as const;
export type AgentStatus = (typeof AGENT_STATUSES)[number];

export const SKILL_STATUSES = ['ENABLED', 'DISABLED'] as const;
export type SkillStatus = (typeof SKILL_STATUSES)[number];

export const MCP_AUTH_TYPES = ['NONE', 'BEARER_TOKEN', 'QUERY_PARAM'] as const;
export type McpAuthType = (typeof MCP_AUTH_TYPES)[number];

export const MCP_SERVER_STATUSES = ['DRAFT', 'ENABLED', 'DISABLED'] as const;
export type McpServerStatus = (typeof MCP_SERVER_STATUSES)[number];

/** Safe Agent response shape. Identity and credential fields are intentionally absent. */
export interface Phase2AgentResponse {
  id: string;
  name: string;
  description: string;
  promptMode: AgentPromptMode;
  promptConfig: Record<string, unknown> | null;
  systemPrompt: string;
  modelName: string | null;
  status: AgentStatus;
  version: number;
  skillIds: string[];
  capabilityKeys: string[];
  createdAt: string;
  updatedAt: string;
}

export interface Phase2SkillResponse {
  id: string;
  name: string;
  description: string;
  instruction: string;
  outputRequirement: string;
  status: SkillStatus;
  version: number;
  capabilityKeys: string[];
  createdAt: string;
  updatedAt: string;
  packageMode?: string | null;
  packageHash?: string | null;
  entrypoints?: SkillEntrypointView[] | null;
}

export interface Phase2ModelResponse {
  name: string;
  displayName: string;
  isDefault: boolean;
  available: boolean;
}

export interface Phase2McpServerResponse {
  id: string;
  name: string;
  serverUrl: string;
  authType: McpAuthType;
  authName: string | null;
  status: McpServerStatus;
  credentialConfigured: boolean;
  lastCheckStatus: string | null;
  lastCheckCode: string | null;
  lastCheckedAt: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface Phase2McpToolResponse {
  id: string;
  toolName: string;
  runtimeName: string;
  description: string;
  inputSchema: Record<string, unknown>;
  enabled: boolean;
  available: boolean;
  version: number;
}

export type Phase2ManagementData =
  | Phase2AgentResponse
  | Phase2AgentResponse[]
  | Phase2SkillResponse
  | Phase2SkillResponse[]
  | Phase2ModelResponse[]
  | Phase2McpServerResponse
  | Phase2McpServerResponse[]
  | Phase2McpToolResponse[];
