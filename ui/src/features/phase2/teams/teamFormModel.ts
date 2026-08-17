import type { Phase2TeamResponse } from '@/contracts/phase2';
import type { TeamUpdateRequest, TeamWriteRequest } from '@/services/phase2/teams';

export const MAX_TEAM_MEMBERS = 20;

export interface TeamFormState {
  id: string | null;
  name: string;
  description: string;
  masterAgentId: string | null;
  memberAgentIds: string[];
  version: number | null;
  masterAgentName: string | null;
}

export function emptyTeamFormState(): TeamFormState {
  return {
    id: null,
    name: '',
    description: '',
    masterAgentId: null,
    memberAgentIds: [],
    version: null,
    masterAgentName: null,
  };
}

export function teamToFormState(team: Phase2TeamResponse): TeamFormState {
  return {
    id: team.id,
    name: team.name,
    description: team.description,
    masterAgentId: team.masterAgentId,
    memberAgentIds: [...team.memberAgentIds],
    version: team.version,
    masterAgentName: team.masterAgentName,
  };
}

export function validateTeamForm(form: TeamFormState): string | null {
  if (!form.name.trim()) {
    return '请填写团队名称';
  }
  if (form.name.trim().length > 128) {
    return '团队名称不能超过 128 个字符';
  }
  if (!form.description.trim()) {
    return '请填写团队描述';
  }
  if (form.description.trim().length > 1000) {
    return '团队描述不能超过 1000 个字符';
  }
  if (!form.masterAgentId) {
    return '请选择主 Agent（必须是已上线的 Agent）';
  }
  if (form.memberAgentIds.length === 0) {
    return '请至少选择 1 个子 Agent';
  }
  if (form.memberAgentIds.length > MAX_TEAM_MEMBERS) {
    return `子 Agent 最多 ${MAX_TEAM_MEMBERS} 个`;
  }
  if (form.memberAgentIds.includes(form.masterAgentId)) {
    return '主 Agent 不能同时作为子 Agent';
  }
  return null;
}

export function formStateToCreateRequest(form: TeamFormState): TeamWriteRequest {
  return {
    name: form.name.trim(),
    description: form.description.trim(),
    masterAgentId: form.masterAgentId ?? '',
    memberAgentIds: [...form.memberAgentIds],
  };
}

export function formStateToUpdateRequest(form: TeamFormState): TeamUpdateRequest {
  return {
    ...formStateToCreateRequest(form),
    version: form.version ?? 0,
  };
}
