import { zipSync } from 'fflate';

const SKILL_MD = 'SKILL.md';
const MAX_IMPORT_ZIP_BYTES = 10 * 1024 * 1024;

function hasSkillMd(paths: string[]): boolean {
  return paths.some((path) => path === SKILL_MD || path.endsWith(`/${SKILL_MD}`));
}

export async function zipSkillFolderFiles(files: File[]): Promise<File> {
  if (files.length === 0) {
    throw new Error('请选择含 SKILL.md 的文件夹');
  }
  const named = files.map((file) => ({
    file,
    path: (file.webkitRelativePath || file.name).replace(/\\/g, '/'),
  })).filter((entry) => entry.path && !entry.path.endsWith('/'));
  if (!hasSkillMd(named.map((entry) => entry.path))) {
    throw new Error('所选文件夹必须包含 SKILL.md');
  }
  const entries: Record<string, Uint8Array> = {};
  for (const { file, path } of named) {
    entries[path] = new Uint8Array(await file.arrayBuffer());
  }
  const zipped = zipSync(entries);
  if (zipped.byteLength > MAX_IMPORT_ZIP_BYTES) {
    throw new Error('Skill 包超过 10MB 上限');
  }
  return new File([zipped], 'skill-package.zip', { type: 'application/zip' });
}

export function validateSkillZipFile(file: File): string | null {
  if (!file) {
    return '请选择 zip 文件';
  }
  const name = file.name.toLowerCase();
  if (!name.endsWith('.zip') && file.type !== 'application/zip' && file.type !== 'application/x-zip-compressed') {
    return '请上传 zip 格式的 Skill 包';
  }
  if (file.size > MAX_IMPORT_ZIP_BYTES) {
    return 'Skill 包超过 10MB 上限';
  }
  return null;
}
