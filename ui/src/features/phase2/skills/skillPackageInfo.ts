import type { SkillEntrypointView, SkillPackageMode } from '@/contracts';

/** Additive fields backends may attach without breaking Phase2SkillResponse. */
export interface SkillPackageInfoView {
  packageMode?: SkillPackageMode | string | null;
  packageHash?: string | null;
  entrypoints?: SkillEntrypointView[] | null;
}

export function readSkillPackageInfo(skill: unknown): SkillPackageInfoView {
  if (!skill || typeof skill !== 'object') return {};
  const rec = skill as Record<string, unknown>;
  return {
    packageMode:
      typeof rec.packageMode === 'string' ? rec.packageMode : null,
    packageHash:
      typeof rec.packageHash === 'string' ? rec.packageHash : null,
    entrypoints: Array.isArray(rec.entrypoints)
      ? (rec.entrypoints as SkillEntrypointView[])
      : null,
  };
}
