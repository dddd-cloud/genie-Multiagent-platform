import type {
  AgentPromptMode,
  Phase2AgentResponse,
} from '@/contracts/phase2';
import type {
  AgentCreateRequest,
  AgentUpdateRequest,
} from '@/services/phase2/internalTypes';

export interface AgentFormState {
  name: string;
  description: string;
  promptMode: AgentPromptMode;
  promptConfigText: string;
  systemPrompt: string;
  modelName: string | null;
  skillIds: string[];
  capabilityKeys: string[];
  version: number | null;
  status: Phase2AgentResponse['status'] | null;
}

export function emptyAgentFormState(): AgentFormState {
  return {
    name: '',
    description: '',
    promptMode: 'RAW',
    promptConfigText: '{\n  \n}',
    systemPrompt: '',
    modelName: null,
    skillIds: [],
    capabilityKeys: [],
    version: null,
    status: null,
  };
}

function instructionsFromAgent(agent: Phase2AgentResponse): string {
  const existing = agent.systemPrompt?.trim();
  if (existing) {
    return agent.systemPrompt ?? '';
  }
  const role = agent.promptConfig && typeof agent.promptConfig === 'object'
    ? (agent.promptConfig as Record<string, unknown>).role
    : undefined;
  if (typeof role === 'string' && role.trim()) {
    return `你的角色是${role.trim()}。`;
  }
  return '';
}

export function agentToFormState(agent: Phase2AgentResponse): AgentFormState {
  return {
    name: agent.name,
    description: agent.description,
    promptMode: 'RAW',
    promptConfigText:
      agent.promptConfig == null
        ? '{\n  \n}'
        : JSON.stringify(agent.promptConfig, null, 2),
    systemPrompt: instructionsFromAgent(agent),
    modelName: agent.modelName,
    skillIds: [...agent.skillIds],
    capabilityKeys: [...agent.capabilityKeys],
    version: agent.version,
    status: agent.status,
  };
}

export type PromptConfigParseResult =
  | { ok: true; value: Record<string, unknown> | null }
  | { ok: false; error: string };

export function parsePromptConfigText(
  text: string,
  mode: AgentPromptMode,
): PromptConfigParseResult {
  if (mode === 'RAW') {
    return {
      ok: true,
      value: null
    };
  }
  const trimmed = text.trim();
  if (!trimmed) {
    return {
      ok: false,
      error: 'STRUCTURED 模式需要有效的 JSON 对象'
    };
  }
  try {
    const parsed: unknown = JSON.parse(trimmed);
    if (
      parsed === null ||
      typeof parsed !== 'object' ||
      Array.isArray(parsed)
    ) {
      return {
        ok: false,
        error: 'promptConfig 必须是 JSON 对象'
      };
    }
    return {
      ok: true,
      value: parsed as Record<string, unknown>
    };
  } catch {
    return {
      ok: false,
      error: 'promptConfig JSON 格式无效'
    };
  }
}

export function validateAgentForm(state: AgentFormState): string | null {
  if (!state.name.trim()) {
    return '请填写名称';
  }
  if (!state.systemPrompt.trim()) {
    return '请填写指令';
  }
  return null;
}

function toPromptFields(state: AgentFormState): {
  promptMode: AgentPromptMode;
  promptConfig: Record<string, unknown> | null;
  systemPrompt: string;
} {
  return {
    promptMode: 'RAW',
    promptConfig: null,
    systemPrompt: state.systemPrompt,
  };
}

export function formStateToCreateRequest(
  state: AgentFormState,
): AgentCreateRequest {
  const prompt = toPromptFields(state);
  return {
    name: state.name.trim(),
    description: state.description.trim(),
    ...prompt,
    modelName: null,
    skillIds: [...state.skillIds],
    capabilityKeys: [...state.capabilityKeys],
  };
}

export function formStateToUpdateRequest(
  state: AgentFormState,
): AgentUpdateRequest {
  if (state.version == null) {
    throw new Error('缺少版本号，无法保存');
  }
  return {
    ...formStateToCreateRequest(state),
    version: state.version,
  };
}

export function agentStatusLabel(status: Phase2AgentResponse['status'] | null | undefined): string {
  switch (status) {
    case 'ONLINE':
      return '已上线';
    case 'OFFLINE':
      return '已下线';
    case 'DRAFT':
      return '草稿';
    default:
      return '';
  }
}
