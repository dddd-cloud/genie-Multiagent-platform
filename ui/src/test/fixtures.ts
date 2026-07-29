import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/** Repo root: ui/src/test → ../../.. */
export const REPO_ROOT = path.resolve(__dirname, '../../..');

export const FIXTURES_ROOT = path.join(
  REPO_ROOT,
  'docs',
  'mvp-contract',
  'fixtures',
);

export function fixturePath(...segments: string[]): string {
  return path.join(FIXTURES_ROOT, ...segments);
}

export function readFixtureText(...segments: string[]): string {
  return fs.readFileSync(fixturePath(...segments), 'utf8');
}

export function readFixtureJson<T = unknown>(...segments: string[]): T {
  return JSON.parse(readFixtureText(...segments)) as T;
}
