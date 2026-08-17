import type { PageResponse } from '@/contracts';
import type { Phase2TeamResponse } from '@/contracts/phase2';
import { phase2Delete, phase2Get, phase2Post, phase2Put } from './client';
import type { Phase2VersionBody } from './internalTypes';

const TEAMS_BASE = '/api/v2/teams';

export interface TeamWriteRequest {
  name: string;
  description: string;
  masterAgentId: string;
  memberAgentIds: string[];
}

export interface TeamUpdateRequest extends TeamWriteRequest {
  version: number;
}

export async function listTeams(
  signal?: AbortSignal,
  page = 1,
  pageSize = 100,
) {
  const pageResponse = await phase2Get<PageResponse<Phase2TeamResponse>>(
    TEAMS_BASE,
    { page, pageSize },
    signal,
  );
  return pageResponse?.items ?? [];
}

export function getTeam(id: string, signal?: AbortSignal) {
  return phase2Get<Phase2TeamResponse>(`${TEAMS_BASE}/${id}`, undefined, signal);
}

export function createTeam(body: TeamWriteRequest, signal?: AbortSignal) {
  return phase2Post<Phase2TeamResponse>(TEAMS_BASE, body, signal);
}

export function updateTeam(
  id: string,
  body: TeamUpdateRequest,
  signal?: AbortSignal,
) {
  return phase2Put<Phase2TeamResponse>(`${TEAMS_BASE}/${id}`, body, signal);
}

export function deleteTeam(
  id: string,
  body: Phase2VersionBody,
  signal?: AbortSignal,
) {
  return phase2Delete<null>(`${TEAMS_BASE}/${id}`, body, signal);
}
