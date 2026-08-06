import type { PageResponse } from '@/contracts';
import type { Phase2SkillResponse } from '@/contracts/phase2';
import { phase2Delete, phase2Get, phase2Post, phase2Put } from './client';
import type {
  Phase2VersionBody,
  SkillCreateRequest,
  SkillUpdateRequest,
} from './internalTypes';

const SKILLS_BASE = '/api/v2/skills';

export async function listSkills(
  signal?: AbortSignal,
  page = 1,
  pageSize = 100,
) {
  const pageResponse = await phase2Get<PageResponse<Phase2SkillResponse>>(
    SKILLS_BASE,
    {
      page,
      pageSize,
    },
    signal,
  );
  return pageResponse?.items ?? [];
}

export function getSkill(id: string, signal?: AbortSignal) {
  return phase2Get<Phase2SkillResponse>(`${SKILLS_BASE}/${id}`, undefined, signal);
}

export function createSkill(body: SkillCreateRequest, signal?: AbortSignal) {
  return phase2Post<Phase2SkillResponse>(SKILLS_BASE, body, signal);
}

export function updateSkill(
  id: string,
  body: SkillUpdateRequest,
  signal?: AbortSignal,
) {
  return phase2Put<Phase2SkillResponse>(`${SKILLS_BASE}/${id}`, body, signal);
}

export function deleteSkill(
  id: string,
  body: Phase2VersionBody,
  signal?: AbortSignal,
) {
  return phase2Delete<null>(`${SKILLS_BASE}/${id}`, body, signal);
}

export function enableSkill(
  id: string,
  body: Phase2VersionBody,
  signal?: AbortSignal,
) {
  return phase2Post<Phase2SkillResponse>(
    `${SKILLS_BASE}/${id}/enable`,
    body,
    signal,
  );
}

export function disableSkill(
  id: string,
  body: Phase2VersionBody,
  signal?: AbortSignal,
) {
  return phase2Post<Phase2SkillResponse>(
    `${SKILLS_BASE}/${id}/disable`,
    body,
    signal,
  );
}
