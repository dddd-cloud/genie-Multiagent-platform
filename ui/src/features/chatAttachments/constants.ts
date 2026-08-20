export const COMPOSER_ATTACHMENT_MAX_FILES = 10;
export const COMPOSER_ATTACHMENT_MAX_BYTES = 10 * 1024 * 1024;
export const COMPOSER_ATTACHMENT_ACCEPT = '.md,.txt,.py,.csv,.json,.doc,.docx,.pdf';
export const COMPOSER_ATTACHMENT_TYPES = [
  'md',
  'txt',
  'py',
  'csv',
  'json',
  'doc',
  'docx',
  'pdf',
] as const;

export type ComposerAttachmentType = (typeof COMPOSER_ATTACHMENT_TYPES)[number];

export const COMPOSER_ATTACHMENT_LABELS: Record<ComposerAttachmentType, string> = {
  md: 'MD',
  txt: 'TXT',
  py: 'PY',
  csv: 'CSV',
  json: 'JSON',
  doc: 'DOC',
  docx: 'DOCX',
  pdf: 'PDF',
};

export function composerAttachmentTypeOf(fileName: string): ComposerAttachmentType | null {
  const dot = fileName.lastIndexOf('.');
  if (dot < 0 || dot === fileName.length - 1) return null;
  const type = fileName.slice(dot + 1).toLowerCase();
  return (COMPOSER_ATTACHMENT_TYPES as readonly string[]).includes(type)
    ? (type as ComposerAttachmentType)
    : null;
}
