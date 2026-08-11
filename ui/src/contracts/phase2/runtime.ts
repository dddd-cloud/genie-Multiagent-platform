export const EXECUTION_MODES = [
  'AUTO',
  'DIRECT',
  'ORCHESTRATED',
] as const;

export type ExecutionMode = (typeof EXECUTION_MODES)[number];

export interface Phase2LocalContext {
  schemaVersion: 1;
  longTermMemory: string;
  conversationSummary: string;
}

export interface Phase2GptQueryRequest {
  sessionId: string;
  requestId: string;
  query: string;
  executionMode: ExecutionMode;
  deepThink: 0 | 1;
  outputStyle: string;
  allowedAgentIds: string[];
  localContext: Phase2LocalContext;
}

export interface AgentCapabilitySummary {
  agentId: string;
  agentVersion: number;
  name: string;
  description: string;
}

export interface AgentRuntimeSkill {
  skillId: string;
  skillVersion: number;
  sortOrder: number;
  instruction: string;
  outputRequirement: string;
  skillKey?: string | null;
  packageMode?: string | null;
  packageVersion?: string | null;
  packageHash?: string | null;
  capabilityKeys?: string[];
  entrypoints?: Array<{
    name: string;
    runtime: string;
    script: string;
    description?: string | null;
    inputSchemaJson?: string | null;
  }>;
}

export interface AgentRuntimeProfile {
  agentId: string;
  agentVersion: number;
  name: string;
  description: string;
  compiledSystemPromptTemplate: string;
  resolvedModelName: string;
  skills: AgentRuntimeSkill[];
  capabilityKeys: string[];
}

export interface ToolBindingView {
  directCapabilities: string[];
  skillCapabilities: Record<string, string[]>;
  invalidCapabilities: string[];
}
