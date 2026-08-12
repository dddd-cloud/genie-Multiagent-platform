export const SKILL_PACKAGE_MODES = [
  'LEGACY_SYNTHETIC',
  'FILESYSTEM',
] as const;

export type SkillPackageMode = (typeof SKILL_PACKAGE_MODES)[number];

export const SKILL_ENTRYPOINT_RUNTIMES = [
  'pyodide',
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
  packages?: string[];
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

/** Frozen cross-module constants for browser skill execution control packets. */
export const BROWSER_SKILL_EXECUTION_SCHEMA_VERSION = 1 as const;
export const BROWSER_SKILL_PRINTER_MESSAGE_TYPE = 'browser_skill_execution' as const;
export const BROWSER_SKILL_SSE_PACKAGE_TYPE = 'skill_execution' as const;
export const BROWSER_SKILL_RESULT_MAP_KEY = 'browserSkillExecution' as const;
export const BROWSER_SKILL_EXECUTION_MANIFEST_PATH = '__joyagent__/execution.json' as const;

export const BrowserSkillExecutionContract = {
  SCHEMA_VERSION: BROWSER_SKILL_EXECUTION_SCHEMA_VERSION,
  PRINTER_MESSAGE_TYPE: BROWSER_SKILL_PRINTER_MESSAGE_TYPE,
  SSE_PACKAGE_TYPE: BROWSER_SKILL_SSE_PACKAGE_TYPE,
  RESULT_MAP_KEY: BROWSER_SKILL_RESULT_MAP_KEY,
  EXECUTION_MANIFEST_PATH: BROWSER_SKILL_EXECUTION_MANIFEST_PATH,
} as const;

export interface BrowserSkillExecutionSignal {
  schemaVersion: number;
  executionId: string;
  skillId: string;
  entrypointName: string;
  packageHash: string;
  timeoutMs: number;
}

export interface BrowserSkillExecutionManifest {
  schemaVersion: number;
  executionId: string;
  entrypointName: string;
  scriptRelativePath: string;
  packages: string[];
  inputJson: string;
}

export interface BrowserSkillExecutionResult {
  schemaVersion: number;
  executionId: string;
  success: boolean;
  outputJson: string | null;
  stdout: string | null;
  stderr: string | null;
  errorCode: string | null;
  message: string | null;
}
