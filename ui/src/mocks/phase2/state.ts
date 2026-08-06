import type { ConversationMessageResponse } from '@/contracts';
import type {
  Phase2AgentResponse,
  Phase2McpServerResponse,
  Phase2McpToolResponse,
  Phase2ModelResponse,
  Phase2SkillResponse,
} from '@/contracts/phase2';
import agentsListFixture from './fixtures/agents-list.json';
import skillsListFixture from './fixtures/skills-list.json';
import mcpServersListFixture from './fixtures/mcp-servers-list.json';
import mcpToolsFixture from './fixtures/mcp-tools.json';
import modelsFixture from './fixtures/models.json';

export type Phase2SseScenario =
  | 'direct-success'
  | 'direct-failure'
  | 'orchestrated-success'
  | 'orchestrated-replan'
  | 'orchestrated-summary-fallback';

export type Phase2MockState = {
  agents: Map<string, Phase2AgentResponse>;
  skills: Map<string, Phase2SkillResponse>;
  mcpServers: Map<string, Phase2McpServerResponse>;
  mcpTools: Map<string, Phase2McpToolResponse[]>;
  models: Phase2ModelResponse[];
  phase2SseScenario: Phase2SseScenario;
  conversationMessages: Map<string, ConversationMessageResponse[]>;
  forceVersionConflict: boolean;
  forceSkillInUse: boolean;
  forceMcpError: boolean;
};

function seedAgents(): Map<string, Phase2AgentResponse> {
  const map = new Map<string, Phase2AgentResponse>();
  for (const agent of agentsListFixture.data as Phase2AgentResponse[]) {
    map.set(agent.id, structuredClone(agent));
  }
  return map;
}

function seedSkills(): Map<string, Phase2SkillResponse> {
  const map = new Map<string, Phase2SkillResponse>();
  for (const skill of skillsListFixture.data as Phase2SkillResponse[]) {
    map.set(skill.id, structuredClone(skill));
  }
  return map;
}

function seedMcpServers(): Map<string, Phase2McpServerResponse> {
  const map = new Map<string, Phase2McpServerResponse>();
  for (const server of mcpServersListFixture.data as Phase2McpServerResponse[]) {
    map.set(server.id, structuredClone(server));
  }
  return map;
}

function seedMcpTools(): Map<string, Phase2McpToolResponse[]> {
  const map = new Map<string, Phase2McpToolResponse[]>();
  const tools = structuredClone(mcpToolsFixture.data) as Phase2McpToolResponse[];
  for (const server of mcpServersListFixture.data as Phase2McpServerResponse[]) {
    map.set(server.id, structuredClone(tools));
  }
  return map;
}

export function createInitialPhase2State(): Phase2MockState {
  return {
    agents: seedAgents(),
    skills: seedSkills(),
    mcpServers: seedMcpServers(),
    mcpTools: seedMcpTools(),
    models: structuredClone(modelsFixture.data) as Phase2ModelResponse[],
    phase2SseScenario: 'direct-success',
    conversationMessages: new Map(),
    forceVersionConflict: false,
    forceSkillInUse: false,
    forceMcpError: false,
  };
}

let phase2State: Phase2MockState = createInitialPhase2State();

export function getPhase2State(): Phase2MockState {
  return phase2State;
}

export function resetPhase2State(partial?: Partial<Phase2MockState>): void {
  phase2State = {
    ...createInitialPhase2State(),
    ...partial,
  };
  if (partial?.agents) {
    phase2State.agents = partial.agents;
  }
  if (partial?.skills) {
    phase2State.skills = partial.skills;
  }
  if (partial?.mcpServers) {
    phase2State.mcpServers = partial.mcpServers;
  }
  if (partial?.mcpTools) {
    phase2State.mcpTools = partial.mcpTools;
  }
  if (partial?.conversationMessages) {
    phase2State.conversationMessages = partial.conversationMessages;
  }
  if (partial?.models) {
    phase2State.models = partial.models;
  }
}

export function getPhase2Messages(
  conversationId: string,
): ConversationMessageResponse[] | null {
  if (!phase2State.conversationMessages.has(conversationId)) {
    return null;
  }
  return phase2State.conversationMessages.get(conversationId)!;
}

export function setPhase2Messages(
  conversationId: string,
  messages: ConversationMessageResponse[],
): void {
  phase2State.conversationMessages.set(conversationId, messages);
}
