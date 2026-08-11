export const SKILL_PACKAGE_MODES = [
  'LEGACY_SYNTHETIC',
  'FILESYSTEM',
] as const;

export type SkillPackageMode = (typeof SKILL_PACKAGE_MODES)[number];

export const SKILL_ENTRYPOINT_RUNTIMES = [
  'python',
  'node',
] as const;

export type SkillEntrypointRuntime = (typeof SKILL_ENTRYPOINT_RUNTIMES)[number];

export interface SkillEntrypointView {
  name: string;
  runtime: SkillEntrypointRuntime;
  script: string;
  description?: string | null;
  inputSchemaJson?: string | null;
}

export interface SkillRuntimePackage {
  skillId: string;
  skillVersion: number;
  sortOrder: number;
  status: string;
  skillKey: string;
  name: string;
  description: string;
  packageMode: SkillPackageMode;
  packageVersion: string;
  packageHash: string;
  instructionMarkdown: string;
  outputRequirement: string;
  resourceManifest: string[];
  entrypoints: SkillEntrypointView[];
  capabilityKeys: string[];
}
