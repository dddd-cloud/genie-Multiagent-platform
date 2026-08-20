import { describe, expect, it } from 'vitest';
import {
  COMPOSER_ATTACHMENT_MAX_FILES,
  composerAttachmentTypeOf,
} from '../constants';

describe('composer attachment constants', () => {
  it('accepts the supported types and caps at 10 files', () => {
    expect(COMPOSER_ATTACHMENT_MAX_FILES).toBe(10);
    expect(composerAttachmentTypeOf('a.md')).toBe('md');
    expect(composerAttachmentTypeOf('a.TXT')).toBe('txt');
    expect(composerAttachmentTypeOf('a.py')).toBe('py');
    expect(composerAttachmentTypeOf('a.csv')).toBe('csv');
    expect(composerAttachmentTypeOf('a.json')).toBe('json');
    expect(composerAttachmentTypeOf('a.doc')).toBe('doc');
    expect(composerAttachmentTypeOf('a.docx')).toBe('docx');
    expect(composerAttachmentTypeOf('a.pdf')).toBe('pdf');
    expect(composerAttachmentTypeOf('a.exe')).toBeNull();
  });
});
