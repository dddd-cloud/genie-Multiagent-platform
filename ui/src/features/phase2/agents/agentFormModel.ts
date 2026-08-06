import type {
  AgentPromptMode,
  Phase2AgentResponse,
} from '@/contracts/phase2';
import type {
  AgentCreateRequest,
  AgentUpdateRequest,
  PromptPreviewRequest,
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
    promptMode: 'STRUCTURED',
    promptConfigText: '{\n  \n}',
    systemPrompt: '',
    modelName: null,
    skillIds: [],
    capabilityKeys: [],
    version: null,
    status: null,
  };
}

export function agentToFormState(agent: Phase2AgentResponse): AgentFormState {
  return {
    name: agent.name,
    description: agent.description,
    promptMode: agent.promptMode,
    promptConfigText:
      agent.promptConfig == null
        ? '{\n  \n}'
        : JSON.stringify(agent.promptConfig, null, 2),
    systemPrompt: agent.systemPrompt ?? '',
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
    return '请填写 Agent 名称';
  }
  if (state.promptMode === 'STRUCTURED') {
    const parsed = parsePromptConfigText(state.promptConfigText, 'STRUCTURED');
    if (!parsed.ok) {
      return parsed.error;
    }
  }
  if (state.promptMode === 'RAW' && !state.systemPrompt.trim()) {
    return 'RAW 模式需要填写 systemPrompt';
  }
  return null;
}

function toPromptFields(state: AgentFormState): {
  promptMode: AgentPromptMode;
  promptConfig: Record<string, unknown> | null;
  systemPrompt: string;
} {
  if (state.promptMode === 'RAW') {
    return {
      promptMode: 'RAW',
      promptConfig: null,
      systemPrompt: state.systemPrompt,
    };
  }
  const parsed = parsePromptConfigText(state.promptConfigText, 'STRUCTURED');
  return {
    promptMode: 'STRUCTURED',
    promptConfig: parsed.ok ? parsed.value : null,
    systemPrompt: '',
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
    modelName: state.modelName,
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

export function formStateToPreviewRequest(
  state: AgentFormState,
): PromptPreviewRequest {
  const prompt = toPromptFields(state);
  return {
    name: state.name.trim() || undefined,
    description: state.description.trim() || undefined,
    ...prompt,
    modelName: state.modelName,
    skillIds: [...state.skillIds],
  };
}

export function moveSkillId(
  skillIds: string[],
  index: number,
  direction: 'up' | 'down',
): string[] {
  const next = [...skillIds];
  const target = direction === 'up' ? index - 1 : index + 1;
  if (index < 0 || index >= next.length || target < 0 || target >= next.length) {
    return skillIds;
  }
  const tmp = next[index]!;
  next[index] = next[target]!;
  next[target] = tmp;
  return next;
}
