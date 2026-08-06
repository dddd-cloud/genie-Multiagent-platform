import type { AgentPromptMode, McpAuthType } from '@/contracts/phase2';

/** UI form / service input — mapped to wire in agents.ts */
export interface AgentCreateRequest {
  name: string;
  description: string;
  promptMode: AgentPromptMode;
  promptConfig: Record<string, unknown> | null;
  systemPrompt: string;
  modelName: string | null;
  skillIds: string[];
  capabilityKeys: string[];
}

/** UI form / service input — mapped to wire in agents.ts */
export interface AgentUpdateRequest {
  name: string;
  description: string;
  promptMode: AgentPromptMode;
  promptConfig: Record<string, unknown> | null;
  systemPrompt: string;
  modelName: string | null;
  skillIds: string[];
  capabilityKeys: string[];
  version: number;
}

/** Backend wire: skill binding on agent write / prompt preview. */
export interface AgentSkillBindingWire {
  skillId: string;
  sortOrder: number;
}

/** Backend wire body for agent create/update. */
export interface AgentWriteWire {
  name: string;
  description: string;
  promptMode: AgentPromptMode;
  /** JSON text, not an object. */
  promptConfig: string | null;
  systemPrompt: string;
  modelName: string | null;
  skills: AgentSkillBindingWire[];
  capabilityKeys: string[];
  version?: number;
}

export interface SkillCreateRequest {
  name: string;
  description: string;
  instruction: string;
  outputRequirement: string;
  capabilityKeys: string[];
}

export interface SkillUpdateRequest {
  name: string;
  description: string;
  instruction: string;
  outputRequirement: string;
  capabilityKeys: string[];
  version: number;
}

/** Write-only credential; never include credentialConfigured. */
export interface McpServerCreateRequest {
  name: string;
  serverUrl: string;
  authType: McpAuthType;
  authName: string | null;
  credential?: string;
}

/** Write-only credential; never include credentialConfigured. */
export interface McpServerUpdateRequest {
  name: string;
  serverUrl: string;
  authType: McpAuthType;
  authName: string | null;
  credential?: string;
  clearCredential?: boolean;
  version: number;
}

export interface AgentTestRequest {
  query: string;
}

/** UI-facing preview input — mapped to wire in agents.ts */
export interface PromptPreviewRequest {
  promptMode: AgentPromptMode;
  promptConfig: Record<string, unknown> | null;
  systemPrompt: string;
  modelName?: string | null;
  skillIds: string[];
  /** Optional UI-only fields ignored by wire mapper. */
  name?: string;
  description?: string;
}

/** Backend wire request for prompt preview. */
export interface PromptPreviewWireRequest {
  promptMode: AgentPromptMode;
  promptConfig: string | null;
  systemPrompt: string;
  modelName: string | null;
  skills: AgentSkillBindingWire[];
}

export interface PromptSkillFragmentWire {
  skillId: string;
  skillVersion: number;
  sortOrder: number;
}

/** Backend wire response for prompt preview. */
export interface PromptPreviewWireResponse {
  compiledSystemPromptTemplate: string;
  skillFragments: PromptSkillFragmentWire[];
  resolvedModelName: string | null;
  codePointLength: number;
}

/** Normalized UI preview result. */
export interface PromptPreviewResponse {
  preview: string;
  skillFragments?: PromptSkillFragmentWire[];
  resolvedModelName?: string | null;
  codePointLength?: number;
}

/** UI capability picker row — mapped from Phase2McpToolResponse. */
export interface ToolCapabilityItem {
  key: string;
  displayName: string;
  available: boolean;
}

export interface SetToolEnabledRequest {
  enabled: boolean;
  version: number;
}

export interface Phase2VersionBody {
  version: number;
}

export interface MemoryAnalyzeTurnRequest {
  conversationId: string;
  userMessage: string;
  assistantMessage: string;
  currentLongTermMemory: string;
  turnStatus: string;
}

export interface MemorySummarizeTurnWire {
  turnNo: number;
  userMessage: string;
  assistantMessage: string;
  assistantStatus: string;
}

export interface MemorySummarizeRequest {
  conversationId: string;
  currentSummary: string;
  newTurns: MemorySummarizeTurnWire[];
}

export interface AgentTaskResultWire {
  status: 'SUCCESS' | 'FAILURE';
  output: string | null;
  errorCode: string | null;
  retryable: boolean;
}

/** Real Agent test response from `/api/v2/agents/{id}/test`. */
export interface AgentTestResponse {
  model: string | null;
  skillSummary: string[];
  capabilityKeys: string[];
  result: AgentTaskResultWire;
  elapsedMillis: number;
  progressEventCount: number;
}
